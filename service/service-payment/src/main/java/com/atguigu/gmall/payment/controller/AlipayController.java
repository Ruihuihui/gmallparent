package com.atguigu.gmall.payment.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;
//  因为不是这个注解  @RestController  所以想显示到页面要加一个 @ResponseBody 注解
@Controller
@RequestMapping("/api/payment/alipay")
public class AlipayController {
    @Autowired
    private AlipayService alipayService;

    @Autowired
    private PaymentService paymentService;

    @RequestMapping("submit/{orderId}")
    @ResponseBody
    public String submitOrder(@PathVariable(value = "orderId") Long orderId, HttpServletResponse response){
        String from = "";
        try {
            from = alipayService.createaliPay(orderId);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return from;
    }


    /**
     * 同步回调   交易成功后 同步回调到成功页面
     * @return
     */
    @RequestMapping("callback/return")
    public String callback(){
        return "redirect:"+ AlipayConfig.return_order_url;
    }


    /**
     * 异步回调   需要做内网穿透
     * 把数据通过异步拿过来   @RequestParam Map<String,String> paramMap
     */
    @RequestMapping("callback/notify")
    @ResponseBody
    public String aliPayNotify(@RequestParam Map<String,String> paramMap) {

        boolean signVerified = false;
        //按照前端传过来的数据格式 进行获取对应的参数
        String trade_status = paramMap.get("trade_status");
        String out_trade_no = paramMap.get("out_trade_no");
        try {
            //支付宝 验证签名通过返回 true
            signVerified = AlipaySignature.rsaCheckV1(paramMap, AlipayConfig.alipay_public_key, AlipayConfig.charset, AlipayConfig.sign_type);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        //验证签名通过后执行内部逻辑
        if (signVerified) {
            //验证参数
           // 只有支付状态是  TRADE_SUCCESS  或者 TRADE_FINISHED  才是支付成功
            if("TRADE_SUCCESS".equals(trade_status) || "TRADE_FINISHED".equals(trade_status)){
                //支付成功 更改支付状态
                //防止用户重复付款
                PaymentInfo paymentInfo = paymentService.getPaymentInfo(out_trade_no, PaymentType.ALIPAY.name());
                //为了防止用户重复付款  订单状态是已支付 PAID  或已关闭 ClOSED 都返回  failure
                if(paymentInfo.getPaymentStatus().equals(PaymentStatus.PAID.name()) ||
                        paymentInfo.getPaymentStatus().equals(PaymentStatus.ClOSED.name())){
                    return "failure";
                }
//                String total_amount = paramMap.get("total_amount");
//                int amount = Integer.parseInt(total_amount);
//                BigDecimal bigDecimal = new BigDecimal(amount);
                //验证金额 和  out_trade_no
                //订单金额和订单详情相等  并且 订单的编号 相等
                //if(paymentInfo.getTotalAmount().compareTo(bigDecimal)==0 && paymentInfo.getOutTradeNo().equals(out_trade_no)){
                    //支付成功 修改订单状态
                paymentService.paySuccess(out_trade_no,PaymentType.ALIPAY.name(),paramMap);
                //返回支付成功
                return "success";

            }
        }else {
            return "failure";
        }
        return "failure";
    }
    @RequestMapping("refund/{orderId}")
    @ResponseBody
    public Result refund(@PathVariable Long orderId){
        boolean flag  = alipayService.refund(orderId);
        return Result.ok(flag);
    }



    /**
     * 是否在支付宝中有交易记录
     * @param orderId
     * @return
     */
    @RequestMapping("checkPayment/{orderId}")
    @ResponseBody
    public Boolean checkPayment(@PathVariable Long orderId){
        Boolean aBoolean = alipayService.checkPayment(orderId);
        return aBoolean;
    }

    /**
     * 查看是否生成支付订单
     * @param outTradeNo
     * @return
     */
    @GetMapping("getPaymentInfo/{outTradeNo}")
    @ResponseBody
    public PaymentInfo getPaymentInfo(@PathVariable String outTradeNo){
        //传入订单编号 和支付方式
        PaymentInfo paymentInfo = paymentService.getPaymentInfo(outTradeNo, PaymentType.ALIPAY.name());
        if (null!=paymentInfo){
            return paymentInfo;
        }
        return null;
    }

    /**
     * 根据订单id   关闭订单
     * @param orderId
     * @return
     */
    @GetMapping("closePay/{orderId}")
    @ResponseBody
    public Boolean closePay(@PathVariable Long orderId){
        Boolean aBoolean = alipayService.closePay(orderId);
        return aBoolean;

    }
}
