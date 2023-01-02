package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.handlers.MybatisMapWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.apache.commons.lang.StringUtils;

import javax.servlet.http.HttpSession;
import java.util.Objects;

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

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到session
        session.setAttribute("code", code);
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
        String cacheCode = (String) session.getAttribute("code");
        String phone = loginForm.getPhone();
        if (StringUtils.isBlank(loginForm.getCode()) || !cacheCode.equals(loginForm.getCode())){
            return Result.fail("验证码错误");
        }else {
            User user = query().eq("phone", phone).one();
            //创建新用户
            if (Objects.isNull(user)){
                user = createUserWithPhone(phone);
            }
            //保存用户信息到session
            session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        }
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
