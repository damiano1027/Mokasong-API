package com.mokasong.controller.view;

import com.mokasong.service.user.UserRegisterService;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@Tag(name = "회원가입", description = "회원가입 API")
public class ViewController {
    @Autowired
    private UserRegisterService userRegisterService;

    /**
     *  URL: 이메일로 전송한 인증 링크
     *
     *  userService.register() 완료시 회원가입이 된 상태이므로 /user/register로 redirect
     *  userService.register() 에서 Exception 발생 시 GlobalExceptionHandler에서 처리
     */

    @Tag(name = "회원가입")
    @GetMapping("/user/register/{register-token}")
    @ApiOperation(value = "회원가입 완료 후 /user/register로 리다이렉트", notes = "회원가입 대기 상태에서 완전한 회원가입 상태로 전환후 /user/register로 redirect합니다.")
    public String register(@PathVariable("register-token") String registerToken) throws Exception {
        userRegisterService.register(registerToken);
        return "redirect:/user/register";
    }

    @Tag(name = "회원가입")
    @GetMapping("/user/register")
    @ApiOperation(value = "회원가입 완료 view", notes = "회원가입 완료가 되었음을 알려주는 페이지를 보여줍니다.")
    public String test() {
        return "register_success";
    }
}
