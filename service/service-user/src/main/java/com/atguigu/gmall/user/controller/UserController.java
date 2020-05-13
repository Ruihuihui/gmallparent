package com.atguigu.gmall.user.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.user.service.UserAddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserAddressService userAddressService;

    /**
     * 根据用户Id 查询用户的收货地址列表！
     * @param userId
     * @return
     */
    @GetMapping("inner/findUserAddressListByUserId/{userId}")
    public List<UserAddress> findUserAddressListByUserId(@PathVariable String userId){
        return userAddressService.findUserAddressListByUserId(userId);
    }

    /**
     * 修改地址
     * @param id
     * @return
     */
    @PostMapping ("inner/updateUserAddressById/{id}")
    public Result updateUserAddressById(@PathVariable Long id){
        UserAddress userAddress = new UserAddress();
        userAddress.setId(id);
        userAddressService.updateById(userAddress);
        return Result.ok();
    }

    /**
     * 删除地址
     * @param id
     * @return
     */
    @DeleteMapping("inner/uremoveUserAddressById/{id}")
    public Result uremoveUserAddressById(@PathVariable Long id){
        userAddressService.removeById(id);
        return Result.ok();
    }

}
