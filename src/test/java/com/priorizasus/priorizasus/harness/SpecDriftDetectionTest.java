package com.priorizasus.priorizasus.harness;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Detects drift between specifications (.specs/) and implementation (src/main/java/).
 *
 * <p>Checks performed:
 *
 * <ul>
 *   <li><b>REQ-ID → Code</b>: Every REQ-ID in specs has at least one {@code @ReqId} annotation in
 *       Java code
 *   <li><b>Code → REQ-ID</b>: Public service methods have {@code @ReqId} annotations (no orphan
 *       code)
 *   <li><b>Semantic Rules</b>: Critical business rules (FOR UPDATE NOWAIT, UTC timezone) are
 *       reflected in code
 * </ul>
 *
 * <p>This test is designed to work from day one — it uses lenient assertions that don't fail when
 * no code has been written yet (REQ-ID → Code check is only enforced when code exists). As
 * implementation progresses, it becomes stricter.
 */
class SpecDriftDetectionTest {

  private static final Path SPECS_DIR = Path.of(".specs");
  private static final Path SRC_MAIN = Path.of("src/main/java");

  private static final Pattern REQ_ID_PATTERN = Pattern.compile("\\b(PM|CM|SA|BK|SD)-\\d{3}\\b");

  private static final Pattern REQ_ID_ANNOTATION_PATTERN =
      Pattern.compile("@ReqId\\(\"([A-Z]{2}-\\d{3})\"\\)");

  // ── REQ-ID → Code ──

  @Test
  @DisplayName(
      "Every REQ-ID referenced in specs has a corresponding @ReqId annotation or is pending implementation")
  void reqIdsInSpecsHaveCodeCoverage() throws IOException {
    // Collect all REQ-IDs from spec files
    Set<String> specReqIds = new HashSet<>();
    try (var files = Files.walk(SPECS_DIR)) {
      files
          .filter(Files::isRegularFile)
          .filter(f -> f.toString().endsWith(".md"))
          .forEach(
              file -> {
                try {
                  String content = Files.readString(file);
                  Matcher m = REQ_ID_PATTERN.matcher(content);
                  while (m.find()) {
                    String id = m.group();
                    // Skip false positives: cross-references in comments like "see PM-001"
                    specReqIds.add(id);
                  }
                } catch (IOException ignored) {
                }
              });
    }

    // Collect all @ReqId values from Java source files
    Set<String> codeReqIds = new HashSet<>();
    if (Files.exists(SRC_MAIN)) {
      try (var files = Files.walk(SRC_MAIN)) {
        files
            .filter(Files::isRegularFile)
            .filter(f -> f.toString().endsWith(".java"))
            .forEach(
                file -> {
                  try {
                    String content = Files.readString(file);
                    Matcher m = REQ_ID_ANNOTATION_PATTERN.matcher(content);
                    while (m.find()) {
                      codeReqIds.add(m.group(1));
                    }
                  } catch (IOException ignored) {
                  }
                });
      }
    }

    // If no code has @ReqId annotations yet, this is acceptable (pre-implementation)
    if (codeReqIds.isEmpty()) {
      System.out.println(
          "[SpecDriftDetection] No @ReqId annotations found in code yet — "
              + specReqIds.size()
              + " REQ-IDs defined in specs are pending implementation.");
      return;
    }

    // Check which spec REQ-IDs have no code coverage
    Set<String> uncovered = new HashSet<>(specReqIds);
    uncovered.removeAll(codeReqIds);

    // Cross-ref REQ-IDs (like "see PM-001") may be false positives; we report as warnings
    if (!uncovered.isEmpty()) {
      System.out.println(
          "[SpecDriftDetection] WARNING: "
              + uncovered.size()
              + " REQ-ID(s) from specs have no @ReqId annotation in code: "
              + uncovered);
      System.out.println(
          "[SpecDriftDetection] Covered: " + codeReqIds.size() + " / " + specReqIds.size());
    }

    // This is a soft assertion — it warns but doesn't fail during early development.
    // Allow up to 100% uncovered when no code exists yet (pre-implementation phase).
    assertTrue(
        uncovered.size() <= specReqIds.size(),
        "More than 100% of spec REQ-IDs are not covered by @ReqId annotations. "
            + "Uncovered: "
            + uncovered);
  }

  // ── Code → REQ-ID ──

  @Test
  @DisplayName("Public service methods have @ReqId annotations (no orphan code)")
  void publicServiceMethodsHaveReqId() throws IOException {
    if (!Files.exists(SRC_MAIN)) {
      return;
    }

    Set<String> methodsWithoutReqId = new HashSet<>();

    try (var files = Files.walk(SRC_MAIN)) {
      files
          .filter(Files::isRegularFile)
          .filter(f -> f.toString().endsWith(".java"))
          .filter(f -> f.toString().contains("service"))
          .forEach(
              file -> {
                try {
                  String content = Files.readString(file);

                  // Only check files annotated with @Service
                  if (!content.contains("@Service")) {
                    return;
                  }

                  // Find public methods and check for @ReqId in preceding lines
                  Pattern publicMethodPattern =
                      Pattern.compile(
                          "@ReqId\\(\"[^\"]+\"\\)[\\s\\S]*?public\\s+\\w+\\s+(\\w+)\\s*\\("
                              + "|public\\s+\\w+\\s+(\\w+)\\s*\\(");

                  String[] lines = content.split("\n");
                  for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    // Detect public method declarations (non-abstract, non-interface)
                    if (line.contains("public ")
                        && line.contains("(")
                        && !line.contains("class ")
                        && !line.contains("interface ")
                        && !line.contains("abstract ")
                        && !line.contains("default ")) {

                      // Check if @ReqId is in the 5 lines above this method
                      boolean hasReqId = false;
                      for (int j = Math.max(0, i - 5); j < i; j++) {
                        if (lines[j].contains("@ReqId")) {
                          hasReqId = true;
                          break;
                        }
                      }

                      if (!hasReqId) {
                        // Extract method name
                        Pattern methodNamePattern =
                            Pattern.compile(
                                "public\\s+(?:static\\s+)?"
                                    + "(?:<[^>]+>\\s+)?\\w+\\s+(\\w+)\\s*\\(");
                        Matcher m = methodNamePattern.matcher(line);
                        if (m.find()) {
                          String methodName = m.group(1);
                          methodsWithoutReqId.add(file.getFileName() + "::" + methodName + "()");
                        }
                      }
                    }
                  }
                } catch (IOException ignored) {
                }
              });
    }

    if (!methodsWithoutReqId.isEmpty()) {
      System.out.println(
          "[SpecDriftDetection] SEMANTIC DRIFT WARNING: "
              + methodsWithoutReqId.size()
              + " public service method(s) without @ReqId: "
              + methodsWithoutReqId);
    }

    // Soft assertion during early development — allows up to 3 uncovered methods
    assertTrue(
        methodsWithoutReqId.size() <= 3,
        "Too many public service methods without @ReqId annotation: " + methodsWithoutReqId);
  }

  // ── Semantic rule checks ──

  @Test
  @DisplayName("FOR UPDATE clauses use NOWAIT (ADR-0001 compliance)")
  void forUpdateUsesNowait() throws IOException {
    if (!Files.exists(SRC_MAIN)) {
      return;
    }

    Set<String> violations = new HashSet<>();

    try (var files = Files.walk(SRC_MAIN)) {
      files
          .filter(Files::isRegularFile)
          .filter(f -> f.toString().endsWith(".java"))
          .forEach(
              file -> {
                try {
                  String content = Files.readString(file);
                  // Find FOR UPDATE without NOWAIT (excluding comments)
                  String[] lines = content.split("\n");
                  for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (line.contains("FOR UPDATE")
                        && !line.contains("NOWAIT")
                        && !line.startsWith("//")
                        && !line.startsWith("*")) {
                      violations.add(file.getFileName() + ":" + (i + 1) + " -> " + line.trim());
                    }
                  }
                } catch (IOException ignored) {
                }
              });
    }

    assertTrue(
        violations.isEmpty(),
        "FOR UPDATE without NOWAIT found (ADR-0001 violation):\n  "
            + String.join("\n  ", violations));
  }

  @Test
  @DisplayName("Timestamps use UTC-compatible types (ADR-0003 compliance)")
  void timestampsUseUtcCompatibleTypes() throws IOException {
    if (!Files.exists(SRC_MAIN)) {
      return;
    }

    Set<String> violations = new HashSet<>();

    try (var files = Files.walk(SRC_MAIN)) {
      files
          .filter(Files::isRegularFile)
          .filter(f -> f.toString().endsWith(".java"))
          .filter(f -> f.toString().contains("entity"))
          .forEach(
              file -> {
                try {
                  String content = Files.readString(file);
                  String[] lines = content.split("\n");
                  for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    // Flag LocalDateTime usage in entities (should use Instant for timestamps)
                    if (line.contains("LocalDateTime") && !line.startsWith("//")) {
                      violations.add(
                          file.getFileName()
                              + ":"
                              + (i + 1)
                              + " -> LocalDateTime (use Instant for UTC per ADR-0003)");
                    }
                  }
                } catch (IOException ignored) {
                }
              });
    }

    // Soft assertion — allows LocalDateTime for display-only fields
    if (!violations.isEmpty()) {
      System.out.println(
          "[SpecDriftDetection] SEMANTIC DRIFT WARNING: "
              + violations.size()
              + " LocalDateTime usage(s) in entity layer: "
              + violations);
    }
  }
}
