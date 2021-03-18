/*
 * <<
 *  Davinci
 *  ==
 *  Copyright (C) 2016 - 2020 EDP
 *  ==
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *        http://www.apache.org/licenses/LICENSE-2.0
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  >>
 *
 */

package edp.davinci.server.inteceptor;

import edp.davinci.commons.util.CollectionUtils;
import edp.davinci.commons.util.JSONUtils;
import edp.davinci.commons.util.StringUtils;
import edp.davinci.core.dao.PlatformMapper;
import edp.davinci.core.dao.entity.Platform;
import edp.davinci.core.dao.entity.PlatformExample;
import edp.davinci.server.annotation.AuthIgnore;
import edp.davinci.server.annotation.AuthShare;
import edp.davinci.server.commons.Constants;
import edp.davinci.server.commons.ErrorMsg;
import edp.davinci.server.controller.ResultMap;
import edp.davinci.server.dao.UserExtendMapper;
import edp.davinci.server.enums.HttpCodeEnum;
import edp.davinci.core.dao.entity.User;
import edp.davinci.server.service.AuthenticationService;
import edp.davinci.server.util.TokenUtils;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static edp.davinci.server.commons.Constants.AUTH_CODE;

public class PlatformAuthInterceptor implements HandlerInterceptor {

    @Autowired
    private PlatformMapper platformMapper;

    @Autowired
    private UserExtendMapper userMapper;

    @Autowired
    private BeanFactory beanFactory;

    @Autowired
    private TokenUtils tokenUtils;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        HandlerMethod handlerMethod = null;
        
        try {
            handlerMethod = (HandlerMethod) handler;
        } catch (Exception e) {
            response.setStatus(HttpCodeEnum.NOT_FOUND.getCode());
            return false;
        }
        
        Method method = handlerMethod.getMethod();

        AuthIgnore ignoreAuthMethod = method.getAnnotation(AuthIgnore.class);
        if (handler instanceof HandlerMethod && null != ignoreAuthMethod) {
            return true;
        }

        ResultMap resultMap = new ResultMap();

        Map<String, String[]> parameterMap = request.getParameterMap();
        if (null == parameterMap) {
            response.setStatus(HttpCodeEnum.UNAUTHORIZED.getCode());
            resultMap.fail(HttpCodeEnum.UNAUTHORIZED.getCode())
                    .message(ErrorMsg.ERR_AUTHENTICATION);
            response.getWriter().print(JSONUtils.toString(resultMap));
            return false;
        }

        if (!parameterMap.containsKey(AUTH_CODE) || null == parameterMap.get(AUTH_CODE) || parameterMap.get(AUTH_CODE).length == 0) {
            response.setStatus(HttpCodeEnum.UNAUTHORIZED.getCode());
            resultMap.fail(HttpCodeEnum.UNAUTHORIZED.getCode())
                    .message(ErrorMsg.ERR_AUTHENTICATION);
            response.getWriter().print(JSONUtils.toString(resultMap));
            return false;
        }

        String authCode = parameterMap.get(AUTH_CODE)[0];
        if (StringUtils.isEmpty(authCode)) {
            response.setStatus(HttpCodeEnum.UNAUTHORIZED.getCode());
            resultMap.fail(HttpCodeEnum.UNAUTHORIZED.getCode())
                    .message(ErrorMsg.ERR_AUTHENTICATION);
            response.getWriter().print(JSONUtils.toString(resultMap));
            return false;
        }

        PlatformExample example = new PlatformExample();
        example.createCriteria().andCodeEqualTo(authCode);
        List<Platform> list = platformMapper.selectByExample(example);
        
        if (CollectionUtils.isEmpty(list)) {
            response.setStatus(HttpCodeEnum.UNAUTHORIZED.getCode());
            resultMap.fail(HttpCodeEnum.UNAUTHORIZED.getCode())
                    .message(ErrorMsg.ERR_AUTHENTICATION);
            response.getWriter().print(JSONUtils.toString(resultMap));
            return false;
        }

        Platform platform = list.get(0); 
        User user = null;
        AuthShare authShareMethod = method.getAnnotation(AuthShare.class);
        if (null != authShareMethod) {
            String token = request.getHeader(Constants.TOKEN_HEADER_STRING);
            if (!StringUtils.isEmpty(token) && token.startsWith(Constants.TOKEN_PREFIX)) {
                String username = tokenUtils.getUsername(token);
                user = userMapper.selectByUsername(username);
                if (null != user) {
                    request.setAttribute(Constants.CURRENT_USER, user);
                } else {
                    response.setStatus(HttpCodeEnum.UNAUTHORIZED.getCode());
                    resultMap.fail(HttpCodeEnum.UNAUTHORIZED.getCode())
                            .message(ErrorMsg.ERR_AUTHENTICATION);
                    response.getWriter().print(JSONUtils.toString(resultMap));
                    return false;
                }
            }
        } else {
            AuthenticationService authenticationService = (AuthenticationService) beanFactory.getBean(platform.getPlatform() + "AuthenticationService");
            try {
                user = authenticationService.checkUser(platform ,parameterMap);
                if (null == user) {
                    response.setStatus(HttpCodeEnum.FORBIDDEN.getCode());
                    resultMap.fail(HttpCodeEnum.FORBIDDEN.getCode()).message(ErrorMsg.ERR_PERMISSION);
                    response.getWriter().print(JSONUtils.toString(resultMap));
                    return false;
                }
            } catch (Exception e) {
                response.setStatus(HttpCodeEnum.FORBIDDEN.getCode());
                resultMap.fail(HttpCodeEnum.FORBIDDEN.getCode()).message(ErrorMsg.ERR_PERMISSION);
                response.getWriter().print(JSONUtils.toString(resultMap));
                return false;
            }
        }

        request.setAttribute(Constants.CURRENT_USER, user);
        request.setAttribute(Constants.CURRENT_PLATFORM, list);
        return true;
    }
}