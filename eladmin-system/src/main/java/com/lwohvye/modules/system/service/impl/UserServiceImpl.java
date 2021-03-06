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
package com.lwohvye.modules.system.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.lwohvye.config.FileProperties;
import com.lwohvye.exception.BadRequestException;
import com.lwohvye.exception.EntityExistException;
import com.lwohvye.exception.EntityNotFoundException;
import com.lwohvye.modules.security.service.OnlineUserService;
import com.lwohvye.modules.security.service.UserCacheClean;
import com.lwohvye.modules.system.domain.User;
import com.lwohvye.modules.system.repository.UserRepository;
import com.lwohvye.modules.system.service.UserService;
import com.lwohvye.modules.system.service.dto.JobSmallDto;
import com.lwohvye.modules.system.service.dto.RoleSmallDto;
import com.lwohvye.modules.system.service.dto.UserDto;
import com.lwohvye.modules.system.service.dto.UserQueryCriteria;
import com.lwohvye.modules.system.service.mapstruct.UserMapper;
import com.lwohvye.utils.*;
import com.lwohvye.utils.redis.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotBlank;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Zheng Jie
 * @date 2018-11-23
 */
@Service
@RequiredArgsConstructor
//?????????????????????????????????
@CacheConfig(cacheNames = "user")
// TODO: 2021/2/25 Redis?????????????????????????????????
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final FileProperties properties;
    private final RedisUtils redisUtils;
    private final UserCacheClean userCacheClean;
    private final OnlineUserService onlineUserService;

    @Override
    @Cacheable
    @Transactional(rollbackFor = Exception.class)
    public Object queryAll(UserQueryCriteria criteria, Pageable pageable) {
        Page<User> page = userRepository.findAll((root, criteriaQuery, criteriaBuilder) -> QueryHelp.getPredicate(root, criteria, criteriaBuilder), pageable);
        return PageUtil.toPage(page.map(userMapper::toDto));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<UserDto> queryAll(UserQueryCriteria criteria) {
        List<User> users = userRepository.findAll((root, criteriaQuery, criteriaBuilder) -> QueryHelp.getPredicate(root, criteria, criteriaBuilder));
        return userMapper.toDto(users);
    }

    @Override
    @Cacheable(key = " #root.target.getSysName() + 'id:' + #p0")
    @Transactional(rollbackFor = Exception.class)
    public UserDto findById(long id) {
        User user = userRepository.findById(id).orElseGet(User::new);
        ValidationUtil.isNull(user.getId(), "User", "id", id);
        return userMapper.toDto(user);
    }

    @Override
//  ?????????????????????????????????????????????
    @CacheEvict(allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public void create(User resources) {
        if (userRepository.findByUsername(resources.getUsername()) != null) {
            throw new EntityExistException(User.class, "username", resources.getUsername());
        }
        if (userRepository.findByEmail(resources.getEmail()) != null) {
            throw new EntityExistException(User.class, "email", resources.getEmail());
        }
        if (userRepository.findByPhone(resources.getPhone()) != null) {
            throw new EntityExistException(User.class, "phone", resources.getPhone());
        }
        userRepository.save(resources);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true)
    public void update(User resources) throws Exception {
        User user = userRepository.findById(resources.getId()).orElseGet(User::new);
        ValidationUtil.isNull(user.getId(), "User", "id", resources.getId());
        User user1 = userRepository.findByUsername(resources.getUsername());
        User user2 = userRepository.findByEmail(resources.getEmail());
        User user3 = userRepository.findByPhone(resources.getPhone());
        if (user1 != null && !user.getId().equals(user1.getId())) {
            throw new EntityExistException(User.class, "username", resources.getUsername());
        }
        if (user2 != null && !user.getId().equals(user2.getId())) {
            throw new EntityExistException(User.class, "email", resources.getEmail());
        }
        if (user3 != null && !user.getId().equals(user3.getId())) {
            throw new EntityExistException(User.class, "phone", resources.getPhone());
        }
        // ???????????????????????????
        if (!resources.getRoles().equals(user.getRoles())) {
            redisUtils.delete(CacheKey.DATA_USER + resources.getId());
            redisUtils.delete(CacheKey.MENU_USER + resources.getId());
            redisUtils.delete(CacheKey.ROLE_AUTH + resources.getId());
        }
        // ???????????????????????????????????????????????????
        if (!resources.getEnabled()) {
            onlineUserService.kickOutForUsername(resources.getUsername());
        }

//        var convertString4BlobUtil = new ConvertString4BlobUtil<User>();
//        ??????????????????????????????????????????????????????????????????????????????????????????
//        convertString4BlobUtil.convert(user);

        user.setUsername(resources.getUsername());
        user.setEmail(resources.getEmail());
        user.setEnabled(resources.getEnabled());
        user.setRoles(resources.getRoles());
        user.setDept(resources.getDept());
        user.setJobs(resources.getJobs());
        user.setPhone(resources.getPhone());
        user.setNickName(resources.getNickName());
        user.setGender(resources.getGender());
        var description = resources.getDescription();
        if (StrUtil.isNotEmpty(description))
            user.setDescription(description);
        userRepository.save(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true)
    public void updateCenter(User resources) {
        User user = userRepository.findById(resources.getId()).orElseGet(User::new);
        User user1 = userRepository.findByPhone(resources.getPhone());
        if (user1 != null && !user.getId().equals(user1.getId())) {
            throw new EntityExistException(User.class, "phone", resources.getPhone());
        }
        user.setNickName(resources.getNickName());
        user.setPhone(resources.getPhone());
        user.setGender(resources.getGender());
        userRepository.save(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true)
    public void delete(Set<Long> ids) {
        userRepository.deleteAllByIdIn(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDto findByName(String userName) {
        User user = userRepository.findByUsername(userName);
        if (user == null) {
            throw new EntityNotFoundException(User.class, "name", userName);
        } else {
            return userMapper.toDto(user);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true)
    public void updatePass(String username, String pass) {
        userRepository.updatePass(username, pass, new Date());
        flushCache(username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true)
    public Map<String, String> updateAvatar(MultipartFile multipartFile) {
        // ??????????????????
        FileUtil.checkSize(properties.getAvatarMaxSize(), multipartFile.getSize());
        // ???????????????????????????
        String image = "gif jpg png jpeg";
        String fileType = FileUtil.getExtensionName(multipartFile.getOriginalFilename());
        if (ObjectUtil.isNotNull(fileType) && !image.contains(fileType)) {
            throw new BadRequestException("?????????????????????, ????????? " + image + " ??????");
        }
        User user = userRepository.findByUsername(SecurityUtils.getCurrentUsername());
        String oldPath = user.getAvatarPath();
        File file = FileUtil.upload(multipartFile, properties.getPath().getAvatar());
        user.setAvatarPath(Objects.requireNonNull(file).getPath());
        user.setAvatarName(file.getName());
        userRepository.save(user);
        if (StringUtils.isNotBlank(oldPath)) {
            FileUtil.del(oldPath);
        }
        @NotBlank String username = user.getUsername();
        flushCache(username);
        return new HashMap<>(1) {
            {
                put("avatar", file.getName());
            }
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true)
    public void updateEmail(String username, String email) {
        userRepository.updateEmail(username, email);
        flushCache(username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(allEntries = true)
    public void updateEnabled(String username, Boolean enabled) {
        userRepository.updateEnabled(username, enabled);
//        ???????????????????????????????????????
        try {
            onlineUserService.kickOutForUsername(username);
        } catch (Exception e) {
            e.printStackTrace();
        }
        flushCache(username);
    }

    @Override
    public void download(List<UserDto> queryAll, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (UserDto userDTO : queryAll) {
            List<String> roles = userDTO.getRoles().stream().map(RoleSmallDto::getName).collect(Collectors.toList());
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("?????????", userDTO.getUsername());
            map.put("??????", roles);
            map.put("??????", userDTO.getDept().getName());
            map.put("??????", userDTO.getJobs().stream().map(JobSmallDto::getName).collect(Collectors.toList()));
            map.put("??????", userDTO.getEmail());
            map.put("??????", userDTO.getEnabled() ? "??????" : "??????");
            map.put("????????????", userDTO.getPhone());
            map.put("?????????????????????", userDTO.getPwdResetTime());
            map.put("????????????", userDTO.getCreateTime());
            list.add(map);
        }
        FileUtil.downloadExcel(list, response);
    }

    /**
     * ?????? ????????? ??????????????????
     *
     * @param username /
     */
    private void flushCache(String username) {
        userCacheClean.cleanUserCache(username);
    }
}
