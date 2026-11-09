
package com.example.fileprocessing.repository;

import com.example.fileprocessing.entity.JobLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobLogRepository
        extends JpaRepository<JobLog, Integer> {

    Optional<JobLog> findByJobId(String jobId);

    Optional<JobLog> findTopByJobNameStartingWithAndStatusOrderByExecutedAtDesc(
            String jobNamePrefix,
            String status
    );
}
