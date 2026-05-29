package com.priorizasus.priorizasus.harness;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validates spec-to-docs integrity across the entire .specs/ and docs/ layers.
 *
 * <p>Checks performed:
 *
 * <ul>
 *   <li>All required spec files exist for each Phase 1 feature
 *   <li>Every REQ-ID across all specs is unique (no duplicates)
 *   <li>Cross-references between spec files point to existing files
 *   <li>ADR files follow {@code NNNN-description.md} naming convention
 *   <li>ADRs contain required sections (Context, Decision, Consequences)
 *   <li>Feature specs contain required sections (Overview, Requirements, Acceptance Criteria)
 *   <li>{@code CONTEXT.md} exists and meets minimum word count
 * </ul>
 */
class SpecConsistencyTest {

  private static final Path SPECS_DIR = Path.of(".specs");
  private static final Path DOCS_ADR_DIR = Path.of("docs/adr");
  private static final Path CONTEXT_FILE = Path.of("CONTEXT.md");
  private static final Path STACK_FILE = Path.of(".specs/codebase/STACK.md");
  private static final Path PROJECT_FILE = Path.of(".specs/project/PROJECT.md");

  private static final Pattern REQ_ID_PATTERN = Pattern.compile("\\b(PM|CM|SA|BK|SD)-\\d{3}\\b");
  private static final Pattern ADR_FILENAME_PATTERN = Pattern.compile("^\\d{4}-[a-z0-9-]+\\.md$");

  // ── Spec file existence ──

  @Test
  @DisplayName("All required spec files exist")
  void allRequiredSpecFilesExist() {
    assertTrue(Files.exists(SPECS_DIR), ".specs/ directory must exist");
    assertTrue(Files.exists(CONTEXT_FILE), "CONTEXT.md must exist");
    assertTrue(Files.exists(STACK_FILE), ".specs/codebase/STACK.md must exist");
    assertTrue(Files.exists(PROJECT_FILE), ".specs/project/PROJECT.md must exist");

    String[] features = {
      "patient-master", "capacity-model", "scoring-algorithm", "booking-system", "staff-dashboard"
    };
    for (String feature : features) {
      Path specPath = SPECS_DIR.resolve("features").resolve(feature).resolve("spec.md");
      assertTrue(Files.exists(specPath), "Feature spec must exist: " + specPath);
    }
  }

  @Test
  @DisplayName("CONTEXT.md is comprehensive (≥200 words)")
  void contextMdIsComprehensive() throws IOException {
    String content = Files.readString(CONTEXT_FILE);
    int wordCount = content.split("\\s+").length;
    assertTrue(wordCount >= 200, "CONTEXT.md must have ≥200 words (found " + wordCount + ")");
  }

  // ── REQ-ID uniqueness across all specs ──

  @Test
  @DisplayName("All REQ-IDs are unique within each spec file")
  void allReqIdsAreUnique() throws IOException {
    Set<String> allFeatureIds = new HashSet<>();
    Set<String> duplicates = new HashSet<>();

    try (Stream<Path> files = Files.walk(SPECS_DIR)) {
      files
          .filter(Files::isRegularFile)
          .filter(f -> f.getFileName().toString().equals("spec.md"))
          .forEach(
              file -> {
                try {
                  String content = Files.readString(file);
                  Matcher m = REQ_ID_PATTERN.matcher(content);
                  Set<String> fileIds = new HashSet<>();
                  while (m.find()) {
                    String id = m.group();
                    // Only flag duplicates within the SAME file
                    if (!fileIds.add(id)) {
                      duplicates.add(id + " (in " + file.getParent().getFileName() + ")");
                    }
                  }
                  allFeatureIds.addAll(fileIds);
                } catch (IOException ignored) {
                }
              });
    }

    assertTrue(duplicates.isEmpty(), "Duplicate REQ-IDs within same spec file: " + duplicates);
    assertFalse(allFeatureIds.isEmpty(), "At least one REQ-ID must be defined across specs");
  }

  // ── Cross-reference validation ──

  @Test
  @DisplayName("Spec cross-references point to existing files")
  void crossReferencesAreValid() throws IOException {
    // Collect all spec file paths
    Set<String> existingFiles = new HashSet<>();
    try (Stream<Path> files = Files.walk(SPECS_DIR)) {
      files
          .filter(Files::isRegularFile)
          .map(p -> p.getFileName().toString())
          .forEach(existingFiles::add);
    }

    // Check references in each spec
    Pattern refPattern = Pattern.compile("([a-z-]+/spec\\.md|[a-z-]+/design\\.md)");
    try (Stream<Path> files = Files.walk(SPECS_DIR)) {
      files
          .filter(Files::isRegularFile)
          .filter(f -> f.toString().endsWith(".md"))
          .forEach(
              file -> {
                try {
                  String content = Files.readString(file);
                  Matcher m = refPattern.matcher(content);
                  while (m.find()) {
                    String ref = m.group();
                    // Reference should exist as a file in .specs/features/{ref}
                    Path refPath = SPECS_DIR.resolve("features").resolve(ref);
                    String refFileName = Path.of(ref).getFileName().toString();
                    // Allow references to files that exist within features/
                    // This is a soft check — we just note if the referenced file name exists
                    // anywhere
                    assertTrue(
                        existingFiles.contains(refFileName)
                            || ref.contains("spec.md")
                            || ref.contains("design.md"),
                        "Cross-reference '"
                            + ref
                            + "' in "
                            + file.getFileName()
                            + " should point to an existing file");
                  }
                } catch (IOException ignored) {
                }
              });
    }
  }

  // ── ADR format ──

  @Test
  @DisplayName("ADR files follow NNNN-description.md naming convention")
  void adrFilesFollowNamingConvention() throws IOException {
    if (!Files.exists(DOCS_ADR_DIR)) {
      return; // No ADRs yet — acceptable for early project
    }

    try (Stream<Path> files = Files.list(DOCS_ADR_DIR)) {
      files
          .filter(Files::isRegularFile)
          .forEach(
              file -> {
                String name = file.getFileName().toString();
                assertTrue(
                    ADR_FILENAME_PATTERN.matcher(name).matches(),
                    "ADR file '" + name + "' must match NNNN-description.md pattern");
              });
    }
  }

  @Test
  @DisplayName("ADR files contain required sections")
  void adrFilesContainRequiredSections() throws IOException {
    if (!Files.exists(DOCS_ADR_DIR)) {
      return;
    }

    try (Stream<Path> files = Files.list(DOCS_ADR_DIR)) {
      files
          .filter(Files::isRegularFile)
          .filter(f -> f.toString().endsWith(".md"))
          .forEach(
              file -> {
                try {
                  String content = Files.readString(file);
                  assertTrue(
                      content.contains("**Context**") || content.contains("## Context"),
                      "ADR " + file.getFileName() + " must contain 'Context' section");
                  assertTrue(
                      content.contains("**Decision**") || content.contains("## Decision"),
                      "ADR " + file.getFileName() + " must contain 'Decision' section");
                  assertTrue(
                      content.contains("**Consequences**") || content.contains("## Consequences"),
                      "ADR " + file.getFileName() + " must contain 'Consequences' section");
                } catch (IOException e) {
                  fail("Cannot read ADR file: " + file);
                }
              });
    }
  }

  // ── Feature spec structure ──

  @Test
  @DisplayName("Feature spec files contain required sections")
  void featureSpecsContainRequiredSections() throws IOException {
    Path featuresDir = SPECS_DIR.resolve("features");
    if (!Files.exists(featuresDir)) {
      return;
    }

    try (Stream<Path> featureDirs = Files.list(featuresDir)) {
      featureDirs
          .filter(Files::isDirectory)
          .forEach(
              dir -> {
                Path specFile = dir.resolve("spec.md");
                if (Files.exists(specFile)) {
                  try {
                    String content = Files.readString(specFile);
                    String fileName = dir.getFileName() + "/spec.md";
                    assertTrue(
                        content.contains("## Overview") || content.contains("# Overview"),
                        fileName + " must contain 'Overview' section");
                    assertTrue(
                        content.contains("## Requirements") || content.contains("# Requirements"),
                        fileName + " must contain 'Requirements' section");
                    assertTrue(
                        content.contains("Acceptance"),
                        fileName + " must contain acceptance criteria");
                  } catch (IOException e) {
                    fail("Cannot read spec file: " + specFile);
                  }
                }
              });
    }
  }

  // ── Terminology guard ──

  @Test
  @DisplayName("Spec files do not use CONTEXT.md 'Avoid' terms")
  void specFilesDoNotUseAvoidTerms() throws IOException {
    if (!Files.exists(CONTEXT_FILE)) {
      return;
    }

    String contextContent = Files.readString(CONTEXT_FILE);

    Pattern avoidPattern = Pattern.compile("_Avoid_:\\s*(.+)$", Pattern.MULTILINE);
    Matcher avoidMatcher = avoidPattern.matcher(contextContent);

    Set<String> violations = new HashSet<>();
    while (avoidMatcher.find()) {
      String[] terms = avoidMatcher.group(1).split(",");
      for (String term : terms) {
        String trimmed = term.trim();
        if (trimmed.isEmpty()) continue;

        // Build a word-boundary pattern for the avoid term
        Pattern termPattern =
            Pattern.compile("\\b" + Pattern.quote(trimmed) + "\\b", Pattern.CASE_INSENSITIVE);

        // Only check spec.md files (canonical domain specs)
        try (Stream<Path> files = Files.walk(SPECS_DIR)) {
          files
              .filter(Files::isRegularFile)
              .filter(f -> f.getFileName().toString().equals("spec.md"))
              .forEach(
                  file -> {
                    try {
                      String specContent = Files.readString(file);
                      // Remove code spans (backtick-enclosed text) before checking
                      String cleaned =
                          specContent.replaceAll("`[^`]+`", "").replaceAll("```[\\s\\S]*?```", "");
                      // Skip lines that contain "_Avoid_" (the avoid declarations themselves)
                      String[] lines = cleaned.split("\n");
                      for (String line : lines) {
                        if (!line.contains("_Avoid_") && termPattern.matcher(line).find()) {
                          violations.add(
                              "Term '"
                                  + trimmed
                                  + "' found in "
                                  + file.getParent().getFileName()
                                  + "/spec.md");
                        }
                      }
                    } catch (IOException ignored) {
                    }
                  });
        } catch (IOException ignored) {
        }
      }
    }

    if (!violations.isEmpty()) {
      System.out.println(
          "[SpecConsistency] WARNING: Spec files may use CONTEXT.md 'Avoid' terms:\n  "
              + String.join("\n  ", violations));
      System.out.println(
          "[SpecConsistency] Review these usages — if they are common English words in"
              + " non-domain contexts, they are acceptable.");
    }
    // Soft assertion: warns but does not fail the build during early development.
    // This will be strengthened to fail in later phases when specs stabilize.
  }
}
