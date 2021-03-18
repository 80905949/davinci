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

package edp.davinci.server.service.impl;

import edp.davinci.commons.util.StringUtils;
import edp.davinci.server.controller.ResultMap;
import edp.davinci.server.enums.CheckEntityEnum;
import edp.davinci.server.service.CheckEntityService;
import edp.davinci.server.service.CheckService;
import edp.davinci.server.util.TokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@Service
public class CheckServiceImpl implements CheckService {

    @Autowired
    private BeanFactory beanFactory;

    @Autowired
    private TokenUtils tokenUtils;

    @Override
    public ResultMap checkSource(String name, Long id, CheckEntityEnum checkEntityEnum, Long scopeId, HttpServletRequest request) {

    	ResultMap resultMap = new ResultMap(tokenUtils);

        if (StringUtils.isEmpty(name)) {
            log.info("The name of entity({}) is empty", checkEntityEnum.getClazz());
            if (checkEntityEnum.equals(CheckEntityEnum.USER)) {
                return resultMap.fail().message("Name is empty");
            }
            return resultMap.failAndRefreshToken(request).message("Name is empty");
        }

        try {
            String clazz = Class.forName(checkEntityEnum.getClazz()).getTypeName();
            if (StringUtils.isEmpty(clazz)) {
                log.info("Not found entity type:{}", checkEntityEnum.getClazz());
                if (checkEntityEnum.equals(CheckEntityEnum.USER)) {
                    return resultMap.fail().message("Not supported entity type");
                }
                return resultMap.failAndRefreshToken(request).message("Not supported entity type");
            }
        } catch (ClassNotFoundException e) {
            log.error("Not supported entity type:{}", checkEntityEnum.getClazz());
            if (checkEntityEnum.equals(CheckEntityEnum.USER)) {
                resultMap.fail().message("Not supported entity type");
            }
            return resultMap.failAndRefreshToken(request).message("Not supported entity type");
        }

        CheckEntityService checkEntityService = (CheckEntityService) beanFactory.getBean(checkEntityEnum.getService());
        if (checkEntityService.isExist(name, id, scopeId)) {
            if (checkEntityEnum.equals(CheckEntityEnum.USER)) {
                return resultMap.fail().message("The current " + checkEntityEnum.getSource() + " name is already taken");
            }
			return resultMap.failAndRefreshToken(request)
					.message("The current " + checkEntityEnum.getSource() + " name is already taken");
        } else {
            if (checkEntityEnum == CheckEntityEnum.USER) {
                return resultMap.success();
            }
            return resultMap.successAndRefreshToken(request);
        }
    }
}