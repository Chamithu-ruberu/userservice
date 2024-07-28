package com.greensphere.userservice.service;

import com.greensphere.userservice.dto.request.tokenRequest.TokenRequest;
import com.greensphere.userservice.dto.request.userLogin.UserLoginRequest;
import com.greensphere.userservice.dto.request.userRegister.GovUserRegisterRequest;
import com.greensphere.userservice.dto.request.userRegister.SetUpDetailsRequest;
import com.greensphere.userservice.dto.request.userRegister.UserRegisterRequestDto;
import com.greensphere.userservice.dto.request.userRegister.UserRegisterVerifyRequest;
import com.greensphere.userservice.dto.response.BaseResponse;
import com.greensphere.userservice.dto.response.OtpVerifyResponse;
import com.greensphere.userservice.dto.response.notificationServiceResponse.SmsResponse;
import com.greensphere.userservice.dto.response.userLoginResponse.UserLoginResponse;
import com.greensphere.userservice.dto.response.userLoginResponse.UserObj;
import com.greensphere.userservice.entity.AppUser;
import com.greensphere.userservice.entity.Parameter;
import com.greensphere.userservice.entity.Role;
import com.greensphere.userservice.enums.Status;
import com.greensphere.userservice.exceptions.MissingParameterException;
import com.greensphere.userservice.repository.ParameterRepository;
import com.greensphere.userservice.repository.UserRepository;
import com.greensphere.userservice.utils.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

import static com.greensphere.userservice.enums.Status.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl {

    private final UserRepository userRepository;
    private final ApiConnector apiConnector;
    private final RoleServiceImpl roleService;
    private final ParameterRepository parameterRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;


    public void persistUser(AppUser appUser) {
        try {
            userRepository.save(appUser);
        } catch (Exception e) {
            log.error("persistUser-> Exception: {}", e.getMessage(), e);
        }
    }

    public BaseResponse<HashMap<String, Object>> registerInit(UserRegisterRequestDto registerInitRequest) {
        try {
            String mobile = PhoneNumberUtil.formatNumber(registerInitRequest.getMobile());
            String email = registerInitRequest.getEmail();
            String nic = registerInitRequest.getNic();
            Long govId = registerInitRequest.getGovId();

            AppUser appUser;
            List<AppUser> appUsers = userRepository.findAppUsersByNicOrMobileOrEmail(nic, mobile, email);
            if (!appUsers.isEmpty()) {
                if (appUsers.size() > 1) {
                    AppUser isExist = appUsers.stream().
                            filter(a ->
                                    nic.equals(a.getNic()) &&
                                            email.equals(a.getEmail()) &&
                                            mobile.equals(a.getMobile()) &&
                                            govId.equals(registerInitRequest.getGovId()))
                            .findFirst()
                            .orElse(null);
                    if (isExist == null) {
                        log.error("registerInit-> AppUser entered details already exists in the database, but not in the same appUser");
                        return BaseResponse.<HashMap<String, Object>>builder()
                                .code(ResponseCodeUtil.FAILED_CODE)
                                .title(ResponseUtil.FAILED)
                                .message("AppUser entered details already exists for another customer, Please recheck the details you entered.")
                                .build();
                    }
                    appUser = isExist;
                } else {
                    appUser = appUsers.get(0);

                }
            } else {
                // save app appUser in INITIALIZED status
                if (registerInitRequest.getRoleType().equals("ROLE_GOVERNMENT_USER")) {
                    appUser = AppUser.builder()
                            .mobile(mobile)
                            .email(email)
                            .nic(nic)
                            .govId(registerInitRequest.getGovId())
                            .build();
                    Role roleByName = roleService.getRoleByName(registerInitRequest.getRoleType());
                    Set<Role> objects = new HashSet<>();
                    objects.add(roleByName);
                    appUser.setRoles(objects);
                    persistUser(appUser);
                    log.info("registerInit -> govUser saved in INITIATED status, mobile: {}, email: {}, nic: {}, govId :{} ", mobile, email, nic, govId);

                } else {
                    appUser = AppUser.builder()
                            .mobile(mobile)
                            .email(email)
                            .nic(nic)
                            .build();
                    Role roleByName = roleService.getRoleByName(registerInitRequest.getRoleType());
                    Set<Role> objects = new HashSet<>();
                    objects.add(roleByName);
                    appUser.setRoles(objects);
                    persistUser(appUser);
                    log.info("registerInit -> appUser saved in INITIATED status, mobile: {}, email: {}, nic: {}", mobile, email, nic);

                }
            }
            Parameter otpLengthParameter = parameterRepository.findParameterByName(AppConstants.OTP_LENGTH);
            if (otpLengthParameter == null) {
                log.warn("registerInit -> OTP_LENGTH parameter is missing from database");
                throw new MissingParameterException("OTP_LENGTH parameter is missing from database, Please add missing OTP_LENGTH parameter");
            }

            Parameter otpMessageParameter = parameterRepository.findParameterByName(AppConstants.OTP_MESSAGE);
            if (otpMessageParameter == null) {
                log.warn("registerInit -> OTP_MESSAGE parameter is missing from database");
                throw new MissingParameterException("OTP_MESSAGE parameter is missing from database, Please add missing OTP_MESSAGE parameter");
            }
            String otp = RandomNumberGenerator.createRandomReference(Integer.parseInt(otpLengthParameter.getValue()));
            String otpMessage = otpMessageParameter.getValue().replace("<otp>", otp);

            //API CALL NOTIFICATION SERVICE ->
            log.info("registerInit -> sending registration otp to user");
            log.info("** sending otp to user ** ", otpMessage);
            SmsResponse smsResponse = apiConnector.sendSms(mobile, otpMessage);
            String otpStatus = smsResponse.getCode().equals(ResponseCodeUtil.SUCCESS_CODE) ? SENT.name() : FAILED.name();
            appUser.setOtp(otp);
            appUser.setOtpStatus(otpStatus);
            appUser.setOtpAttempts(appUser.getOtpAttempts() + 1);
            appUser.setOtpSentAt(LocalDateTime.now());

            appUser.setStatus(PENDING.name());
            persistUser(appUser);

            HashMap<String, Object> data = new HashMap<>();
            data.put("app_user_id", appUser.getUsername());
            data.put("mobile", mobile);
            data.put("user_role", appUser.getRoles());

            return BaseResponse.<HashMap<String, Object>>builder()
                    .code(ResponseCodeUtil.SUCCESS_CODE)
                    .title(ResponseUtil.SUCCESS)
                    .message("AppUser initiated successfully")
                    .data(data)
                    .build();
        } catch (Exception e) {
            log.error("registerInit -> Exception : {}", e.getMessage(), e);
            return BaseResponse.<HashMap<String, Object>>builder()
                    .code(ResponseCodeUtil.INTERNAL_SERVER_ERROR_CODE)
                    .title(ResponseUtil.INTERNAL_SERVER_ERROR)
                    .message("Error occurred while initializing appUser")
                    .build();
        }
    }

    public BaseResponse<HashMap<String, Object>> registerVerify(UserRegisterVerifyRequest userRegisterVerifyRequest) {
        try {
            AppUser user = userRepository.findAppUserByUsername(userRegisterVerifyRequest.getUsername());
            if (user == null) {
                log.warn("registerVerify -> user not found for this username: {}", userRegisterVerifyRequest.getUsername());
                return BaseResponse.<HashMap<String, Object>>builder()
                        .code(ResponseCodeUtil.FAILED_CODE)
                        .title(ResponseUtil.FAILED)
                        .message("Cannot find user")
                        .build();
            }

            BaseResponse<OtpVerifyResponse> otpVerificationResponse = verifyOtp(user, userRegisterVerifyRequest.getOtp());


            if (!otpVerificationResponse.getCode().equals(ResponseCodeUtil.SUCCESS_CODE)) {
                log.warn("registerVerify -> OTP verification failed: {}", otpVerificationResponse.getCode());
                return BaseResponse.<HashMap<String, Object>>builder()
                        .code(otpVerificationResponse.getCode())
                        .title(otpVerificationResponse.getTitle())
                        .message(otpVerificationResponse.getMessage())
                        .build();
            }
            log.info("registerVerify -> user verified: {}", user.getUsername());
            HashMap<String, Object> data = new HashMap<>();
            data.put("user_id", user.getUsername());
            data.put("user_status", user.getStatus());
            data.put("message", otpVerificationResponse.getMessage());
            return BaseResponse.<HashMap<String, Object>>builder()
                    .code(ResponseCodeUtil.SUCCESS_CODE)
                    .title(ResponseUtil.SUCCESS)
                    .message("user verified successfully")
                    .data(data)
                    .build();

        } catch (Exception e) {
            log.error("registerVerify -> Exception : {}", e.getMessage(), e);
            return BaseResponse.<HashMap<String, Object>>builder()
                    .code(ResponseCodeUtil.INTERNAL_SERVER_ERROR_CODE)
                    .title(ResponseUtil.INTERNAL_SERVER_ERROR)
                    .message("Error occurred while verifying appUser")
                    .build();
        }
    }

    public BaseResponse<HashMap<String, Object>> setUpDetails(SetUpDetailsRequest setUpDetailsRequest) {
        try {
            AppUser user = userRepository.findAppUserByUsername(setUpDetailsRequest.getUsername());
            if (user == null) {
                log.warn("setUpDetails-> Cannot find user for this username: {}", setUpDetailsRequest.getUsername());
                return BaseResponse.<HashMap<String, Object>>builder()
                        .code(ResponseCodeUtil.FAILED_CODE)
                        .title(ResponseUtil.FAILED)
                        .message("Cannot find user")
                        .build();
            }
            // create user authentication tokens for other requests
            List<Role> roles = (List<Role>) user.getRoles();
            if (roles.isEmpty()) {
                log.warn("saveUserCredentials-> user doesn't have any roles");
                return BaseResponse.<HashMap<String, Object>>builder()
                        .code(ResponseCodeUtil.CANNOT_FIND_USER_ROLES)
                        .title(ResponseUtil.FAILED)
                        .message("User doesn't have any roles")
                        .build();
            }
            Role role = roles.stream().filter(r -> r.getName().equals(setUpDetailsRequest.getRoleType())).findAny().orElse(null);
            if (role == null) {
                log.warn("saveUserCredentials -> User doesn't have APP_USER privileges");
                return BaseResponse.<HashMap<String, Object>>builder()
                        .code(ResponseCodeUtil.USER_DOESNT_HAVE_PERMISSION)
                        .title(ResponseUtil.FAILED)
                        .message("User doesn't have app user privileges")
                        .build();
            }
            TokenRequest tokenRequest = TokenRequest.builder()
                    .username(user.getUsername())
                    .role(role.getName())
                    .build();
            String token = jwtUtil.createJwtToken(tokenRequest);
            String refreshToken = jwtUtil.createRefreshToken(tokenRequest);

            user.setFullName(setUpDetailsRequest.getFullName());
            user.setProfilePic(setUpDetailsRequest.getProfile());
            user.setAddressNo(setUpDetailsRequest.getAddressNo());
            user.setAddressStreet(setUpDetailsRequest.getAddressStreet());
            user.setCity(setUpDetailsRequest.getCity());
            user.setStatus(SAVED.name());
            user.setDob(setUpDetailsRequest.getBirthOfDate());
            user.setPassword(setUpDetailsRequest.getPassword());
            user.setPostalCode(setUpDetailsRequest.getPostalCode());
            user.setRegisteredAt(LocalDateTime.now());
            user.setRegisteredAt(LocalDateTime.now());
            persistUser(user);
            log.info("setUpDetails-> User password setup details");


            HashMap<String, Object> userObj = new HashMap<>();
            userObj.put("full_name", user.getFullName());
            userObj.put("nic", user.getNic());
            userObj.put("username", user.getUsername());
            userObj.put("mobile", user.getMobile());
            userObj.put("status", user.getStatus());

            HashMap<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("refresh_token", refreshToken);
            data.put("user", userObj);

            log.info("saveUserCredentials -> User password saved");

            return BaseResponse.<HashMap<String, Object>>builder()
                    .code(ResponseCodeUtil.SUCCESS_CODE)
                    .title(ResponseUtil.SUCCESS)
                    .message("User details setup successful")
                    .data(data)
                    .build();
        } catch (Exception e) {
            log.error("setUpDetails -> Exception : {}", e.getMessage(), e);
            return BaseResponse.<HashMap<String, Object>>builder()
                    .code(ResponseCodeUtil.INTERNAL_SERVER_ERROR_CODE)
                    .title(ResponseUtil.INTERNAL_SERVER_ERROR)
                    .message("Error occurred while saving user details")
                    .build();
        }
    }

    public BaseResponse<OtpVerifyResponse> verifyOtp(AppUser appUser, String otp) throws MissingParameterException {
        try {
            Parameter expiredTimeParameter = parameterRepository.findParameterByName(AppConstants.OTP_EXPIRED_TIME);
            if (expiredTimeParameter == null) {
                log.warn("verifyOtp -> OTP_EXPIRED_TIME parameter is missing from database");
                throw new MissingParameterException("OTP_EXPIRED_TIME parameter is missing from database, Please add missing OTP_EXPIRED_TIME parameter");
            }

            Parameter verifyOtpAttemptsParameter = parameterRepository.findParameterByName(AppConstants.OTP_VERIFY_ATTEMPTS);
            if (verifyOtpAttemptsParameter == null) {
                log.warn("verifyOtp -> OTP_VERIFY_ATTEMPTS parameter is missing from database");
                throw new MissingParameterException("OTP_EXPIRED_TIME parameter is missing from database, Please add missing OTP_VERIFY_ATTEMPTS parameter");
            }

            long expiredTime = Long.parseLong(expiredTimeParameter.getValue());
            int verifyOtpAttempts = Integer.parseInt(verifyOtpAttemptsParameter.getValue());

            if (!appUser.getOtpStatus().equals(SENT.name())) {
                log.warn("verifyOtp -> already verified otp");
                return BaseResponse.<OtpVerifyResponse>builder()
                        .code(ResponseCodeUtil.FAILED_CODE)
                        .message("Already verified otp")
                        .build();
            }

            if (appUser.getVerifyAttempts() >= verifyOtpAttempts) {
                log.warn("verifyOtp -> otp verification attempts exceeded");
                return BaseResponse.<OtpVerifyResponse>builder()
                        .code(ResponseCodeUtil.OTP_ATTEMPTS_EXCEED_ERROR_CODE)
                        .title(ResponseUtil.FAILED)
                        .message("Otp verification attempts exceeded")
                        .build();
            }

            LocalDateTime otpSentTime = appUser.getOtpSentAt();
            LocalDateTime otpExpiredTime = otpSentTime.plusSeconds(expiredTime);

            if (LocalDateTime.now().isAfter(otpExpiredTime)) {
                log.info("verifyOtp-> Otp expired");
                return BaseResponse.<OtpVerifyResponse>builder()
                        .code(ResponseCodeUtil.OTP_EXPIRED)
                        .title(ResponseUtil.FAILED)
                        .message("OTP has expired. Please resend new OTP.")
                        .build();
            }

            boolean matches = passwordEncoder.matches(otp, appUser.getOtp());
            if (matches) {
                appUser.setOtpStatus(VERIFIED.name());
                appUser.setStatus(VERIFIED.name());
                appUser.setOtpAttempts(0);
                appUser.setVerifyAttempts(0);
                persistUser(appUser);
                log.info("verifyOtp -> Otp verified");
                return BaseResponse.<OtpVerifyResponse>builder()
                        .code(ResponseCodeUtil.SUCCESS_CODE)
                        .title(ResponseUtil.SUCCESS)
                        .message("Otp verified")
                        .build();
            } else {
                appUser.setVerifyAttempts(appUser.getVerifyAttempts() + 1);
                persistUser(appUser);
                int remainingAttempts = 3 - appUser.getVerifyAttempts();
                log.info("verifyOtp -> otp verification failed, remaining attempts : {}", remainingAttempts);

                return BaseResponse.<OtpVerifyResponse>builder()
                        .code(ResponseCodeUtil.INVALID_OTP_ERROR_CODE)
                        .title(ResponseUtil.FAILED)
                        .message(remainingAttempts == 0 ? "User attempts are over" : ("Please retype your OTP. You have " + remainingAttempts + " " + (remainingAttempts == 1 ? "attempt" : "attempts") + " left"))
                        .build();
            }
        } catch (Exception e) {
            log.error("verifyOtp -> Exception : {}", e.getMessage(), e);
            if (e instanceof MissingParameterException) {
                throw new MissingParameterException(e.getMessage());
            }
            return BaseResponse.<OtpVerifyResponse>builder()
                    .code(ResponseCodeUtil.INTERNAL_SERVER_ERROR_CODE)
                    .message("Error occurred while verifying otp")
                    .build();
        }
    }

    public BaseResponse<HashMap<String, Object>> govUserSignUp(GovUserRegisterRequest govUserRegisterRequest) {
        try {
            AppUser user = userRepository.findAppUserByUsername(govUserRegisterRequest.getUsername());
            if (user == null) {
                log.warn("setUpDetails -> Cannot find user for this username: {}", govUserRegisterRequest.getUsername());
                return BaseResponse.<HashMap<String, Object>>builder()
                        .code(ResponseCodeUtil.FAILED_CODE)
                        .title(ResponseUtil.FAILED)
                        .message("Cannot find user")
                        .build();
            }
            // create user authentication tokens for other requests
            List<Role> roles = (List<Role>) user.getRoles();
            if (roles.isEmpty()) {
                log.warn("saveUserCredentials-> user doesn't have any roles");
                return BaseResponse.<HashMap<String, Object>>builder()
                        .code(ResponseCodeUtil.CANNOT_FIND_USER_ROLES)
                        .title(ResponseUtil.FAILED)
                        .message("User doesn't have any roles")
                        .build();
            }
            Role role = roles.stream().filter(r -> r.getName().equals("ROLE_GOVERNMENT_USER")).findAny().orElse(null);
            if (role == null) {
                log.warn("saveUserCredentials -> User doesn't have APP_USER privileges");
                return BaseResponse.<HashMap<String, Object>>builder()
                        .code(ResponseCodeUtil.USER_DOESNT_HAVE_PERMISSION)
                        .title(ResponseUtil.FAILED)
                        .message("User doesn't have app user privileges")
                        .build();
            }

            TokenRequest tokenRequest = TokenRequest.builder()
                    .role(role.getName())
                    .username(govUserRegisterRequest.getUsername())
                    .now(LocalDateTime.now())
                    .build();

            String token = jwtUtil.createJwtToken(tokenRequest);
            String refreshToken = jwtUtil.createRefreshToken(tokenRequest);
            user.setStatus(SAVED.name());
            user.setRegisteredAt(LocalDateTime.now());
            user.setPassword(govUserRegisterRequest.getPassword());
            user.setCity(govUserRegisterRequest.getCity());
            user.setFullName(govUserRegisterRequest.getName());
            persistUser(user);

            HashMap<String, Object> userObj = new HashMap<>();
            userObj.put("full_name", user.getFullName());
            userObj.put("status", user.getStatus());
            userObj.put("city", user.getCity());
            userObj.put("email", user.getEmail());
            userObj.put("user_id", user.getGovId());


            HashMap<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("refresh_token", refreshToken);
            data.put("user", userObj);

            return BaseResponse.<HashMap<String, Object>>builder()
                    .code(ResponseCodeUtil.SUCCESS_CODE)
                    .title(ResponseUtil.SUCCESS)
                    .message("User Successfully signUp")
                    .data(data)
                    .build();

        } catch (Exception e) {
            log.error("govUserSignUp -> Exception : {}", e.getMessage(), e);
            return BaseResponse.<HashMap<String, Object>>builder()
                    .code(ResponseCodeUtil.INTERNAL_SERVER_ERROR_CODE)
                    .title(ResponseUtil.INTERNAL_SERVER_ERROR)
                    .message("Error occurred while signup Government User")
                    .build();
        }
    }

    public BaseResponse<UserLoginResponse> login(UserLoginRequest loginRequest) {
        try {
            String password = loginRequest.getPassword();
            AppUser loginUser = userRepository.findAppUserByEmail(loginRequest.getEmail());

            if (loginUser == null) {
                log.warn("There is No user Found -> {}", loginRequest.getEmail());
                return BaseResponse.<UserLoginResponse>builder()
                        .code(ResponseCodeUtil.FAILED_CODE)
                        .title(ResponseUtil.FAILED)
                        .message("App User Not Found")
                        .build();
            }

            return logUser(loginRequest, password, loginUser);

        } catch (Exception e) {
            log.error("govUserSignUp -> Exception : {}", e.getMessage(), e);
            return BaseResponse.<UserLoginResponse>builder()
                    .code(ResponseCodeUtil.INTERNAL_SERVER_ERROR_CODE)
                    .title(ResponseUtil.INTERNAL_SERVER_ERROR)
                    .message("Error occurred while user login")
                    .build();
        }
    }

    private BaseResponse<UserLoginResponse> logUser(UserLoginRequest loginRequest, String password, AppUser loginUser) {
        if (ACTIVE.name().equals(loginUser.getStatus())) {
            try {
                authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), password));
                loginUser.setLoginAttempts(0);
                persistUser(loginUser);

            } catch (BadCredentialsException e) {
                int remainingAttempts = 0;
                log.info("login -> Invalid credentials, user login attempts: " + loginUser.getLoginAttempts());

                Parameter parameter = parameterRepository.findParameterByName(AppConstants.LOGIN_ATTEMPTS);
                if (parameter == null) {
                    log.warn("login -> Missing Parameter -> " + AppConstants.LOGIN_ATTEMPTS);
                    throw new MissingParameterException("Parameter not found for given name: " + AppConstants.LOGIN_ATTEMPTS);
                }

                int attempts = Integer.parseInt(parameter.getValue());

                if (loginUser.getLoginAttempts() < attempts) {
                    log.info("login -> remaining login attempts: " + loginUser.getLoginAttempts());
                    loginUser.setLoginAttempts(loginUser.getLoginAttempts() + 1);
                    persistUser(loginUser);

                    remainingAttempts = attempts - loginUser.getLoginAttempts();
                    if (remainingAttempts < 1) {
                        loginUser.setStatus(DISABLED.name());
                        loginUser.setDisabledReason(AppConstants.LOGIN_ATTEMPTS_EXCEEDED);
                        persistUser(loginUser);
                        log.info("User {} has been disabled due to exceeded login attempts.", loginUser.getUsername());

                    //send login attempts exceeded sms
                    Parameter smsExceeded = parameterRepository.findParameterByName(AppConstants.LOGIN_ATTEMPTS_EXCEEDED_MESSAGE);
                    if (smsExceeded== null) {
                        log.warn("login -> Missing Parameter -> " + AppConstants.LOGIN_ATTEMPTS_EXCEEDED_MESSAGE);
                        throw new MissingParameterException("Parameter not found for given name: " + AppConstants.LOGIN_ATTEMPTS_EXCEEDED_MESSAGE);
                    }


                    log.info("Sending login attempts exceeded message to user {}.", loginUser.getUsername());
                    String value = smsExceeded.getValue();
                    String mobile = loginUser.getMobile();
                    SmsResponse sms = apiConnector.sendSms(mobile, value);
                    if (!ResponseCodeUtil.SUCCESS_CODE.equals(sms.getCode())) {
                        log.error("Failed to send message to user {} at mobile number {}.", loginUser.getUsername(), loginUser.getMobile());
                    }

                        log.error("loguser -> login attempts exceeded");
                        return BaseResponse.<UserLoginResponse>builder()
                                .code(ResponseCodeUtil.OTP_ATTEMPTS_EXCEED_ERROR_CODE)
                                .title(ResponseUtil.FAILED)
                                .message("Login attempts exceeded.Please contact the Bank")
                                .build();
                    } else {
                        log.error("loguser -> attempted failed. You have : " + remainingAttempts);
                        return BaseResponse.<UserLoginResponse>builder()
                                .code(ResponseCodeUtil.FAILED_CODE)
                                .title(ResponseUtil.FAILED)
                                .message("Login attempted failed. You have " + remainingAttempts + " more attempts.")
                                .build();
                    }
                }
            }
        } else {
            log.info("logUser -> Disabled user");
            return BaseResponse.<UserLoginResponse>builder()
                    .code(ResponseCodeUtil.DISABLE_USER_ERROR_CODE)
                    .title(ResponseUtil.FAILED)
                    .message("User is not Active. Please contact the Support Center")
                    .build();
        }

        TokenRequest tokenRequest = TokenRequest.builder()
                .role(loginRequest.getRoleType())
                .username(loginUser.getUsername())
                .now(LocalDateTime.now())
                .build();

        String token = jwtUtil.createJwtToken(tokenRequest);
        String refreshToken = jwtUtil.createRefreshToken(tokenRequest);

        UserLoginResponse data = UserLoginResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .userObj(UserObj.builder()
                        .fullName(loginUser.getFullName())
                        .email(loginUser.getEmail())
                        .govId(loginUser.getGovId())
                        .city(loginUser.getCity())
                        .status(loginUser.getStatus())
                        .build())
                .build();

        return BaseResponse.<UserLoginResponse>builder()
                .code(ResponseCodeUtil.SUCCESS_CODE)
                .title(ResponseUtil.SUCCESS)
                .message("Successfully logged in")
                .data(data)
                .build();
    }
}
