package com.futao.springbootdemo.service.impl;

import com.futao.springbootdemo.dao.UserDao;
import com.futao.springbootdemo.foundation.LogicException;
import com.futao.springbootdemo.model.entity.User;
import com.futao.springbootdemo.model.enums.UserStatusEnum;
import com.futao.springbootdemo.model.system.*;
import com.futao.springbootdemo.service.MailService;
import com.futao.springbootdemo.service.UUIDService;
import com.futao.springbootdemo.service.UserService;
import com.futao.springbootdemo.utils.CommonUtilsKt;
import com.futao.springbootdemo.utils.DateTools;
import com.futao.springbootdemo.utils.PageResultUtils;
import com.futao.springbootdemo.utils.ThreadLocalUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Spring事务超时 = 事务开始时到最后一个Statement创建时时间 + 最后一个Statement的执行时超时时间（即其queryTimeout）。所以在在执行Statement之外的超时无法进行事务回滚。
 * 参考：https://blog.csdn.net/qq_18860653/article/details/79907984
 *
 * @author futao
 * Created on 2018/9/20-15:16.
 */
@Transactional(isolation = Isolation.REPEATABLE_READ, timeout = SystemConfig.SERVICE_TRANSACTION_TIMEOUT_SECOND, rollbackFor = Exception.class)
@Service
public class UserServiceImpl implements UserService {
    /**
     * 密码加盐
     */
    public static final String PWD_SALT = "nobug666";

    @Resource
    private ThreadLocalUtils<User> threadLocalUtils;

    @Autowired
    private UserDao userDao;

    @Resource
    private MailService mailService;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private SystemConfig systemConfig;


    @Override
    public User currentLoginUser() {
        return threadLocalUtils.get();
    }

    @Override
    public User getUserByMobile(String mobile) {
        return userDao.getUserByMobile(mobile);
    }

    @Override
    public User getUserById(String id) {
        return userDao.getUserById(id);
    }

    /**
     * 发送注册邮件验证码
     *
     * @param email 邮箱
     */
    @Override
    public void sendRegisterEmailVerifyCode(String email) {
        //1.检查该邮箱是否已经被注册
        HashMap preRegisterInfo = userDao.getPreRegisterInfo(email);
        //未注册
        if (preRegisterInfo == null) {
            //存储注册信息
            userDao.insertPreRegisterInfo(UUIDService.get(), email);
            //发送注册验证码
            sendRegisterEmail(email);
        }
        //已预注册过
        else
            //已经是正常用户
            if ((boolean) preRegisterInfo.get("usered")) {
                throw LogicException.le(ErrorMessage.LogicErrorMessage.EMAIL_ALREADY_EXIST);
            }
            //潜在用户，还未注册成功
            else {
                sendRegisterEmail(email);
                userDao.updatePreRegisterInfo(preRegisterInfo.get("id").toString(), (int) preRegisterInfo.get("sendTimes"), DateTools.currentTimeStamp());
            }
    }

    private void sendRegisterEmail(String email) {
        //3.判断是否已经发送了邮件且未过期
        if (redisTemplate.opsForValue().get(RedisKeySet.gen(RedisKeySet.REGISTER_EMAIL_CODE, email)) != null) {
            throw LogicException.le(ErrorMessage.LogicErrorMessage.EMAIL_ALREADY_SEND);
        }
        String verifyCode = CommonUtilsKt.numVerifyCode(6);

        //4.TODO("通过消息队列")发送注册邮件
        MailmSingle mailM = new MailmSingle();
        mailM.setTo(email);
        mailM.setSubject("快乐的网站 | 注册验证码");
        mailM.setContent("您的验证码是" + verifyCode);
        mailM.setCc("1185172056@qq.com");

        mailService.sendSimpleEmail(new String[]{mailM.getTo()}, new String[]{mailM.getCc()}, mailM.getSubject(), mailM.getContent());
        //5.将验证码存入redis环境，控制有效期
        redisTemplate.opsForValue().set(RedisKeySet.gen(RedisKeySet.REGISTER_EMAIL_CODE, email), verifyCode, systemConfig.getRegisterMailCodeExpireSecond(), TimeUnit.SECONDS);

    }

    /**
     * TODO("注册功能还有问题")
     *
     * @param username
     * @param password
     * @param age
     * @param mobile
     * @param email
     * @param address
     * @param verifyCode
     * @param sex
     */
    @Override
    public void registerByEmail(String username, String password, int age, String mobile, String email, String address, String verifyCode, int sex) {
        //1.查询该手机号有没有被注册
//        User userInfo = userDao.getUserByMobile(mobile);

        //检查验证码
        Object o = redisTemplate.opsForValue().get(RedisKeySet.gen(RedisKeySet.REGISTER_EMAIL_CODE, email));
        //检查是否过期
        if (o == null) {
            throw LogicException.le(ErrorMessage.LogicErrorMessage.VERIFY_CODE_EXPIRED);
        }
        //检查是否正确
        if (!verifyCode.equals(o.toString())) {
            throw LogicException.le(ErrorMessage.LogicErrorMessage.VERIFY_CODE_ERROR);
        }

        //检查该邮箱是否已经被注册
        if (userDao.getUserIdByEmailAndStatus(email, UserStatusEnum.NORMAL.getCode()) != null) {
            throw LogicException.le(ErrorMessage.LogicErrorMessage.EMAIL_ALREADY_EXIST);
        }
        //更新账号信息与状态
        userDao.registerByEmail(username, CommonUtilsKt.md5(password + PWD_SALT), age, mobile, address, UserStatusEnum.NORMAL.getCode(), sex, email);
    }

    /**
     * 用户登录
     *
     * @param mobile
     * @param password
     * @param request
     * @return
     */
    @Override
    public User login(String mobile, String password, HttpServletRequest request, HttpServletResponse response) {
        String md5Pwd = CommonUtilsKt.md5(password + PWD_SALT);
        User user = userDao.getUserByMobileAndPwd(mobile, md5Pwd);
        if (ObjectUtils.allNotNull(user)) {
            HttpSession session = request.getSession();
            session.setAttribute(Constant.LOGIN_USER_SESSION_KEY, String.valueOf(user.getId()));
            session.setMaxInactiveInterval(systemConfig.getSessionInvalidateSecond());
            Cookie cookie = new Cookie("JSESSIONID", session.getId());
            cookie.setMaxAge(systemConfig.getSessionInvalidateSecond());
            //是否不允许js读取
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            //是否只在https下传输
            cookie.setSecure(false);
            response.addCookie(cookie);
            return user;
        } else {
            throw LogicException.le(ErrorMessage.LogicErrorMessage.MOBILE_OR_PWD_ERROR);
        }
    }

    @Override
    public User userNameLogin(User user, HttpServletRequest request) {
        String md5Pwd = CommonUtilsKt.md5(user.getPassword() + PWD_SALT);
        User byUserNameAndPwd = userDao.getUserByUserNameAndPwd(user.getUsername(), md5Pwd);

        if (ObjectUtils.allNotNull(byUserNameAndPwd)) {
            HttpSession session = request.getSession();
            session.setAttribute(Constant.LOGIN_USER_SESSION_KEY, String.valueOf(user.getId()));
            session.setMaxInactiveInterval(systemConfig.getSessionInvalidateSecond());
            return byUserNameAndPwd;
        } else {
            throw LogicException.le(ErrorMessage.LogicErrorMessage.MOBILE_OR_PWD_ERROR);
        }
    }

    /**
     * @param mobile
     * @param pageNum
     * @param pageSize
     * @param orderBy
     * @return
     */
    @Override
//    @Cacheable(value = "userList")
    public List<User> list(String mobile, int pageNum, int pageSize, String orderBy) {
        PageResultUtils<User> pageResultUtils = new PageResultUtils<>();
        String sql = pageResultUtils.createCriteria(User.class.getSimpleName())
                .orderBy(orderBy)
                .page(pageNum, pageSize)
                .getSql();
        List<User> list = userDao.list(sql);
        User user = new User();

//        List<User> listAdd = this.listAdd();
//        redisTemplate.opsForValue().set("userList", listAdd);
        return list;

    }

    @Override
    public int total() {
        return userDao.total("futao_user");
    }


}
