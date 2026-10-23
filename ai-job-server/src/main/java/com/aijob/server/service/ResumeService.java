package com.aijob.server.service;

import com.aijob.server.entity.Resume;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ResumeService {

    Resume upload(MultipartFile file, Long userId);

    List<Resume> listByUserId(Long userId);

    Resume getById(Long id);

    void delete(Long id, Long userId);

    Resume parse(Long id, Long userId);
}
