package com.mokasong.service.user;

import com.mokasong.domain.user.User;
import com.mokasong.dto.user.UserRegisterDto;
import com.mokasong.dto.user.VerificationCodeCheckDto;
import com.mokasong.exception.CustomExceptionList;
import com.mokasong.exception.custom.UserRegisterFailException;
import com.mokasong.exception.custom.VerificationCodeException;
import com.mokasong.repository.UserMapper;
import com.mokasong.response.BaseResponse;
import com.mokasong.response.NormalResponse;
import com.mokasong.state.RedisCategory;
import com.mokasong.util.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;

import static com.mokasong.exception.CustomExceptionList.*;

@Service
public class UserRegisterServiceImpl implements UserRegisterService {
    private final UserMapper userMapper;
    private final JwtHandler jwtHandler;
    private final AwsSes awsSes;
    private final Coolsms coolsms;
    private final RedisClient redisClient;

    @Autowired
    public UserRegisterServiceImpl(
        UserMapper userMapper, JwtHandler jwtHandler, AwsSes awsSes,
        Coolsms coolsms, RedisClient redisClient) {
        this.userMapper = userMapper;
        this.jwtHandler = jwtHandler;
        this.awsSes = awsSes;
        this.coolsms = coolsms;
        this.redisClient = redisClient;
    }

    @Override
    public BaseResponse getExistenceOfEmail(String email) throws Exception {
        if (this.emailExist(email)) {
            return new NormalResponse("이미 회원정보에 존재하는 이메일입니다.", new HashMap<>() {{
                put("email_existence", true);
            }});
        }

        return new NormalResponse("가입이 가능한 이메일입니다.", new HashMap<>() {{
            put("email_existence", false);
        }});
    }

    @Override
    public BaseResponse getExistenceOfPhoneNumber(String phoneNumber) throws Exception {
        if (this.phoneNumberExist(phoneNumber)) {
            return new NormalResponse("이미 회원정보에 존재하는 휴대폰번호입니다.", new HashMap<>() {{
                put("phone_number_existence", true);
            }});
        }

        return new NormalResponse("가입이 가능한 휴대폰 번호입니다.", new HashMap<>() {{
            put("phone_number_existence", false);
        }});
    }

    // TODO: 만료 되었을시의 상황을 고려해볼 것
    @Override
    public BaseResponse sendVerificationCodeForPhoneNumber(String phoneNumber) throws Exception {
        if (this.phoneNumberExist(phoneNumber)) {
            User user = userMapper.getUserByPhoneNumber(phoneNumber);

            CustomExceptionList phoneNumberAlreadyExist = PHONE_NUMBER_ALREADY_EXIST
                    .setMessage(String.format("%s 이메일로 이미 가입된 계정의 전화번호입니다.", StringHandler.hideIdOfEmail(user.getEmail())));

            throw new UserRegisterFailException(phoneNumberAlreadyExist);
        }

        String verificationCode = RandomStringUtils.randomNumeric(6);
        String messageText = String.format("[Mokasong](회원가입 휴대전화 인증) 인증번호는 [%s]입니다.", verificationCode);

        // TODO: 다시 살리기
        // coolsms.sendMessageToOne(phoneNumber, messageText);
        System.out.println(verificationCode);

        redisClient.setString(RedisCategory.REGISTER_CELLPHONE, phoneNumber, verificationCode, 3);

        return new NormalResponse("인증번호를 전송하였습니다. 3분안에 인증해주세요.", new HashMap<>() {{
            put("is_sent", true);
        }});
    }

    // TODO: 만료되었을 시의 상황을 고려해볼 것
    @Override
    public BaseResponse checkVerificationCodeForPhoneNumber(VerificationCodeCheckDto verificationCodeCheckDto) throws Exception {
        String phoneNumber = verificationCodeCheckDto.getPhone_number();
        String verificationCode = verificationCodeCheckDto.getVerification_code();

        String verificationCodeInRedisServer = redisClient.getString(RedisCategory.REGISTER_CELLPHONE, phoneNumber);

        if (verificationCodeInRedisServer == null) {
            throw new VerificationCodeException(VERIFICATION_TIME_EXPIRE);
        }
        if (!verificationCodeInRedisServer.equals(verificationCode)) {
            throw new VerificationCodeException(VERIFICATION_CODE_NOT_EQUAL);
        }

        String randomString = RandomStringUtils.randomAlphanumeric(100);
        redisClient.setString(RedisCategory.CHANGE_TO_STAND_BY_REGULAR, phoneNumber, randomString, 5);

        redisClient.deleteKey(RedisCategory.REGISTER_CELLPHONE, phoneNumber);

        return new NormalResponse("인증이 완료되었습니다.", new HashMap<>() {{
            put("verification_success", true);
            put("token", randomString);
        }});
    }

    @Override
    @Transactional
    public BaseResponse changeToStandingByRegister(UserRegisterDto userRegisterDto) throws Exception {
        String phoneNumber = userRegisterDto.getPhone_number();
        String email = userRegisterDto.getEmail();

        String verificationTokenInRedisServer = redisClient.getString(RedisCategory.CHANGE_TO_STAND_BY_REGULAR, phoneNumber);
        if (verificationTokenInRedisServer == null) {
            throw new UserRegisterFailException(REQUEST_TIME_EXPIRE_OR_DATA_COUNTERFEIT_DETECTED);
        }
        if (!verificationTokenInRedisServer.equals(userRegisterDto.getVerification_token())) {
            throw new UserRegisterFailException(VERIFICATION_TOKEN_NOT_EQUAL);
        }

        User selectedUserByPhoneNumber = userMapper.getUserByPhoneNumber(phoneNumber);
        if (selectedUserByPhoneNumber != null) {
            if (!selectedUserByPhoneNumber.getIs_deleted()) {
                throw new UserRegisterFailException(PHONE_NUMBER_ALREADY_EXIST);
            } else {
                userMapper.deleteUserById(selectedUserByPhoneNumber.getUser_id());
            }
        }

        User selectedUserByEmail = userMapper.getUserByEmail(email);
        if (selectedUserByEmail != null) {
            if (!selectedUserByEmail.getIs_deleted()) {
                throw new UserRegisterFailException(EMAIL_ALREADY_EXIST);
            } else {
                userMapper.deleteUserById(selectedUserByEmail.getUser_id());
            }
        }

        String registerToken;
        User selectedUserByRegisterToken;
        do {
            registerToken = RandomStringUtils.randomAlphanumeric(200);
            selectedUserByRegisterToken = userMapper.getUserByRegisterToken(registerToken);
        } while (selectedUserByRegisterToken != null);

        User user = new User();
        user.initializeForStandingByRegister(userRegisterDto, registerToken);
        userMapper.createUser(user);

        // TODO: 실제 배포시에는 real host로 바꿀것
        String url = "http://localhost:8080/user/register/" + registerToken;

        // TODO: 실제 배포시에는 html로 만들어 전송할것
        awsSes.sendEmail(user.getEmail(), "[Mokasong] 이메일을 인증해주세요.", url);

        redisClient.deleteKey(RedisCategory.CHANGE_TO_STAND_BY_REGULAR, phoneNumber);

        return new NormalResponse("회원가입 대기 상태로 전환되었습니다.", new HashMap<>() {{
            put("success", true);
        }});
    }

    @Override
    @Transactional
    public void register(String registerToken) throws Exception {
        User user = userMapper.getUserByRegisterToken(registerToken);

        if (user == null) {
            throw new UserRegisterFailException(INVALID_ACCESS);
        }

        user.changeAuthorityToRegular();
        userMapper.updateUser(user);
    }

    private boolean emailExist(String email) {
        User user = userMapper.getUserByEmail(email);

        if ((user == null) || (user.getIs_deleted())) {
            return false;
        }

        return true;
    }

    private boolean phoneNumberExist(String phoneNumber) {
        User user = userMapper.getUserByPhoneNumber(phoneNumber);

        if ((user == null) || (user.getIs_deleted())) {
            return false;
        }

        return true;
    }
}
