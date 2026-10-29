package ru.practicum.ewm.compilation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.compilation.model.CompilationEvent;
import ru.practicum.ewm.compilation.model.CompilationEventId;

import java.util.Collection;
import java.util.List;

public interface CompilationEventRepository extends JpaRepository<CompilationEvent, CompilationEventId> {

    List<CompilationEvent> findByIdCompilationId(Long compilationId);

    List<CompilationEvent> findByIdCompilationIdIn(Collection<Long> compilationIds);

    @Modifying
    @Query("delete from CompilationEvent ce where ce.id.compilationId = :compilationId")
    void deleteByCompilationId(@Param("compilationId") Long compilationId);
}
