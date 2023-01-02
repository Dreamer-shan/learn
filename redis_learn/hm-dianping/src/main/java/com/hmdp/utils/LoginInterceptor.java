package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Objects;

/**
 * @author hongyuan.shan
 * @date 2022/12/25 22:44
 * @description
 */
public class LoginInterceptor implements HandlerInterceptor {
    //在Controller处理之前进行调用
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession();
        Object user = session.getAttribute("user");
        if (Objects.isNull(user)){
            response.setStatus(401);
            return false;
        }
        //保存到threadlocal
        UserHolder.saveUser((UserDTO) user);
        //放行
        return true;
    }


    //清理资源
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
