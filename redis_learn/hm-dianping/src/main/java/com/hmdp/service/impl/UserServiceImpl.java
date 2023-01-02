package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.handlers.MybatisMapWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.info("短信验证码成功,验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        assert  Objects.nonNull(loginForm);
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("手机号格式错误");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        //生成token作为令牌
        String token = UUID.randomUUID().toString(true);
        try{
            if (Objects.nonNull(cacheCode)){
                String phone = loginForm.getPhone();
                if (StringUtils.isBlank(loginForm.getCode()) || !cacheCode.equals(loginForm.getCode())){
                    return Result.fail("验证码错误");
                }else {
                    User user = query().eq("phone", phone).one();
                    //创建新用户
                    if (Objects.isNull(user)){
                        user = createUserWithPhone(phone);
                    }
                    UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                    Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
                    String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
                    //保存用户信息到redis
                    stringRedisTemplate.opsForHash().putAll(tokenKey + token, userMap);
                    //设置有效期
                    stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
                }
            }
        }catch (Exception e){
            log.error("redis is null");
        }
        //返回token给客户端，不返回的话拦截器每次获取不到token，直接就拦截了
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
