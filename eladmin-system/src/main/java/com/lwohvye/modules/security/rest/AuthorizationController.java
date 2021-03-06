/*
 *  Copyright 2019-2020 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.lwohvye.modules.security.rest;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSONObject;
import com.lwohvye.annotation.rest.AnonymousDeleteMapping;
import com.lwohvye.annotation.rest.AnonymousGetMapping;
import com.lwohvye.annotation.rest.AnonymousPostMapping;
import com.lwohvye.config.RsaProperties;
import com.lwohvye.config.redis.AuthRedisUtils;
import com.lwohvye.config.redis.AuthSlaveRedisUtils;
import com.lwohvye.exception.BadRequestException;
import com.lwohvye.modules.kafka.service.KafkaProducerService;
import com.lwohvye.modules.security.config.bean.LoginCodeEnum;
import com.lwohvye.modules.security.config.bean.LoginProperties;
import com.lwohvye.modules.security.config.bean.SecurityProperties;
import com.lwohvye.modules.security.security.TokenProvider;
import com.lwohvye.modules.security.service.OnlineUserService;
import com.lwohvye.modules.security.service.dto.AuthUserDto;
import com.lwohvye.modules.security.service.dto.JwtUserDto;
import com.lwohvye.utils.redis.RedisUtils;
import com.lwohvye.utils.RsaUtils;
import com.lwohvye.utils.SecurityUtils;
import com.lwohvye.utils.StringUtils;
import com.lwohvye.utils.result.ResultInfo;
import com.wf.captcha.base.Captcha;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Zheng Jie
 * @date 2018-11-23
 * ???????????????token????????????????????????
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Api(tags = "???????????????????????????")
public class AuthorizationController {
    private final SecurityProperties properties;
    //    ??????
    private final RedisUtils redisUtils;
    //    ???????????????
    private final AuthRedisUtils authRedisUtils;
    private final AuthSlaveRedisUtils authSlaveRedisUtils;
    private final OnlineUserService onlineUserService;
    private final TokenProvider tokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    //    ???????????????
    private final KafkaProducerService kafkaProducerService;

    @Resource
    private LoginProperties loginProperties;


    @ApiOperation("????????????")
    @AnonymousPostMapping(value = "/login")
    // TODO: 2021/4/21 ?????????????????????ip????????????????????????
    public ResponseEntity<Object> login(@Validated @RequestBody AuthUserDto authUser, HttpServletRequest request) throws Exception {

        var username = authUser.getUsername();
        String lockUserKey = username + "||authLocked||";
//        var lockUser = authRedisUtils.get(lockUserKey);
//        if (ObjectUtil.isNotNull(lockUser) && lockUser instanceof Collection col ? CollUtil.isNotEmpty(col) : ObjectUtil.isNotEmpty(lockUser)) {
//        if (ObjectUtil.isNotEmpty(lockUser)) {
        // TODO: 2021/4/22 ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
//        if (authSlaveRedisUtils.hasKey(lockUserKey))
//            throw new BadRequestException("????????????????????????????????????");

        // ????????????
        String password = RsaUtils.decryptByPrivateKey(RsaProperties.privateKey, authUser.getPassword());
        // ???????????????
        String code = (String) authSlaveRedisUtils.get(authUser.getUuid());
        // ???????????????
        authRedisUtils.delete(authUser.getUuid());
        if (StringUtils.isBlank(code)) {
            throw new BadRequestException("??????????????????????????????");
        }
        if (StringUtils.isBlank(authUser.getCode()) || !authUser.getCode().equalsIgnoreCase(code)) {
            throw new BadRequestException("???????????????");
        }
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(username, password);

        Authentication authentication;
        try {
            authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        } catch (BadCredentialsException e) {
            String ip = StringUtils.getIp(request);
            var infoMap = new JSONObject();
            infoMap.put("ip", ip);
            infoMap.put("lockUserKey", lockUserKey);
            infoMap.put("username", username);
//            ????????????
            kafkaProducerService.sendCallbackMessage("auth-failed", infoMap.toJSONString());
            throw e;
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        // ????????????????????????????????????????????????
        // UserDetails userDetails = userDetailsService.loadUserByUsername(userInfo.getUsername());
        // Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        // SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = tokenProvider.createToken(authentication);
        final JwtUserDto jwtUserDto = (JwtUserDto) authentication.getPrincipal();
        // ??????????????????
        onlineUserService.save(jwtUserDto, token, request);
        // ?????? token ??? ????????????
        Map<String, Object> authInfo = new HashMap<>(2) {
            {
                put("token", properties.getTokenStartWith() + token);
                put("user", jwtUserDto);
            }
        };
        if (loginProperties.isSingleLogin()) {
            //???????????????????????????token
            onlineUserService.checkLoginOnUser(username, token);
        }
//        ???????????????????????????????????????
        kafkaProducerService.sendCallbackMessage("auth-log", jwtUserDto.getUser().toString());
        return ResponseEntity.ok(authInfo);
    }

    @ApiOperation("??????????????????")
    @GetMapping(value = "/info")
    public ResponseEntity<Object> getUserInfo() {
        return ResponseEntity.ok(SecurityUtils.getCurrentUser());
    }

    @ApiOperation("???????????????")
    @AnonymousGetMapping(value = "/code")
    public ResponseEntity<Object> getCode() {
        // ?????????????????????
        Captcha captcha = loginProperties.getCaptcha();
        String uuid = properties.getCodeKey() + IdUtil.simpleUUID();
        //????????????????????? arithmetic???????????? >= 2 ??????captcha.text()??????????????????????????????
        String captchaValue = captcha.text();
        if (captcha.getCharType() - 1 == LoginCodeEnum.arithmetic.ordinal() && captchaValue.contains(".")) {
            captchaValue = captchaValue.split("\\.")[0];
        }
        // ??????
        authRedisUtils.set(uuid, captchaValue, loginProperties.getLoginCode().getExpiration(), TimeUnit.MINUTES);
        // ???????????????
        Map<String, Object> imgResult = new HashMap<>(2) {
            {
                put("img", captcha.toBase64());
                put("uuid", uuid);
            }
        };
        return ResponseEntity.ok(imgResult);
    }

    @ApiOperation("????????????")
    @AnonymousDeleteMapping(value = "/logout")
    public ResponseEntity<Object> logout(HttpServletRequest request) {
        onlineUserService.logout(tokenProvider.getToken(request));
        return new ResponseEntity<>(ResultInfo.success(), HttpStatus.OK);
    }
}
