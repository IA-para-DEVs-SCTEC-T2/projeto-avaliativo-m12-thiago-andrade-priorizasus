package com.priorizasus.priorizasus.repository;

import com.priorizasus.priorizasus.entity.BookingToken;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for {@link BookingToken} entities. */
@Repository
public interface BookingTokenRepository extends JpaRepository<BookingToken, Long> {

  Optional<BookingToken> findByToken(String token);

  Optional<BookingToken>
      findFirstByPatient_IdAndWeekStartAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
          Long patientId, LocalDate weekStart, Instant now);
}
