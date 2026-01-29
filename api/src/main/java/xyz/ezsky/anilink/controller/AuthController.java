package xyz.ezsky.anilink.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import xyz.ezsky.anilink.model.dto.LoginRequest;
import xyz.ezsky.anilink.model.entity.User;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.UserInfoVO;
import xyz.ezsky.anilink.service.UserService;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth/")
@Tag(name = "认证服务", description = "进行认证操作的相关接口")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("login")
    @Operation(summary = "登录", description = "登录并返回token")
    public ApiResponseVO<SaTokenInfo> doLogin(@RequestBody LoginRequest loginRequest) {
        // 验证用户登录
        Optional<User> userOpt = userService.validateLogin(loginRequest.getUsername(), loginRequest.getPassword());

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // 使用用户ID登录
            StpUtil.login(user.getId());
            return ApiResponseVO.success(StpUtil.getTokenInfo(), "登录成功");
        }

        return ApiResponseVO.fail(500, "用户名或密码错误");
    }

    @PostMapping("currentUser")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的详细信息")
    public ApiResponseVO<UserInfoVO> getCurrentUserInfo() {
        // 获取当前登录用户的ID
        Object loginId = StpUtil.getLoginId();

        if (loginId == null) {
            return ApiResponseVO.fail(401, "未登录");
        }

        Long userId = Long.valueOf(loginId.toString());
        UserInfoVO userInfo = userService.getUserInfoById(userId);

        if (userInfo == null) {
            return ApiResponseVO.fail(404, "用户不存在");
        }

        return ApiResponseVO.success(userInfo, "获取成功");
    }

}

