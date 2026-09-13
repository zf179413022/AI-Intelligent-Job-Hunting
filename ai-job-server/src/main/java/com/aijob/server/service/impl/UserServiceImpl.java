package com.aijob.server.service.impl;

import com.aijob.server.dto.LoginRequest;
import com.aijob.server.dto.RegisterRequest;
import com.aijob.server.entity.User;
import com.aijob.server.mapper.UserMapper;
import com.aijob.server.security.JwtUtil;
import com.aijob.server.service.UserService;
import com.aijob.server.vo.LoginVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserServiceImpl(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void register(RegisterRequest request) {

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());

        User existingUser = userMapper.selectOne(wrapper);

        if (existingUser != null) {
            throw new RuntimeException("用户名已经存在");
        }

        User user = new User();

        user.setUsername(request.getUsername());

        // BCrypt 加密密码
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setEmail(request.getEmail());
        user.setRole("USER");

        userMapper.insert(user);
    }

    @Override
    public LoginVO login(LoginRequest request) {

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());

        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        boolean passwordMatch = passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        );

        if (!passwordMatch) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRole()
        );

        return new LoginVO(
                token,
                user.getId(),
                user.getUsername(),
                user.getRole()
        );
    }
}
