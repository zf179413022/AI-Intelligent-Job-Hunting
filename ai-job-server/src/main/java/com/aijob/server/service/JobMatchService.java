package com.aijob.server.service;

import com.aijob.server.dto.JobMatchRequest;
import com.aijob.server.entity.JobMatch;

import java.util.List;

public interface JobMatchService {

    JobMatch create(JobMatchRequest request, Long userId);

    List<JobMatch> listByUserId(Long userId);

    JobMatch getById(Long id, Long userId);

    void delete(Long id, Long userId);
}
