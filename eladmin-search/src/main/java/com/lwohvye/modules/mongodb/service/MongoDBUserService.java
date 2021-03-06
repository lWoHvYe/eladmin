package com.lwohvye.modules.mongodb.service;

import com.lwohvye.base.BaseService;

/**
 * @author Hongyan Wang
 * @date 2021年04月17日 13:53
 */
public interface MongoDBUserService extends BaseService {

    Object findAll();

    void updateUsers();

}
