package com.aijob.server.service;

import com.aijob.server.dto.LoginRequest;
import com.aijob.server.dto.RegisterRequest;
import com.aijob.server.vo.LoginVO;

public interface UserService {

    void register(RegisterRequest request);

    LoginVO login(LoginRequest request);
}
