/*
 *  Copyright 2020-2022 lWoHvYe
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
package com.lwohvye.config;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.expression.BeanFactoryAccessor;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.env.Environment;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ServerAwareNamingStrategy extends SpringPhysicalNamingStrategy implements ApplicationContextAware {

    @Autowired
    private Environment env;

    private final StandardEvaluationContext context = new StandardEvaluationContext();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context.addPropertyAccessor(new BeanFactoryAccessor());
        this.context.setBeanResolver(new BeanFactoryResolver(applicationContext));
        this.context.setRootObject(applicationContext);
    }

    /**
     * @param name
     * @param jdbcEnvironment
     * @return org.hibernate.boot.model.naming.Identifier
     * @description ?????????????????????????????????????????????????????????sql,??????hql??????
     * ??????@Table?????????@JoinTable???????????????
     * @author Hongyan Wang
     * @date 2021/4/23 11:06 ??????
     */
    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        String nameStr = name.getText();
//        ?????? #{xxx:y}  ??????xxx??????????????????????????????y??????
        var prefix = ParserContext.TEMPLATE_EXPRESSION.getExpressionPrefix();
        var suffix = ParserContext.TEMPLATE_EXPRESSION.getExpressionSuffix();
        if (StrUtil.isNotBlank(nameStr) && nameStr.startsWith(prefix) && nameStr.endsWith(suffix)) {
            var index = nameStr.indexOf(":");
            var key = nameStr.substring(2, ObjectUtil.equal(index, -1) ? nameStr.length() - 1 : index);
            var defaultValue = ObjectUtil.equal(index, -1) ? "" : nameStr.substring(index + 1, nameStr.length() - 1);
            var value = env.getProperty(key);
            //??????SimpleElasticsearchPersistentEntity ????????????,???tableName?????????????????????????????????
//            Expression expression = this.parser.parseExpression(prefix + key + suffix, ParserContext.TEMPLATE_EXPRESSION);
//            log.error(expression.getValue(this.context, String.class));
            return Identifier.toIdentifier(StrUtil.isNotBlank(value) ? value : defaultValue);
        } else {
            //??????????????????
            return super.toPhysicalTableName(name, jdbcEnvironment);
        }
    }
}
