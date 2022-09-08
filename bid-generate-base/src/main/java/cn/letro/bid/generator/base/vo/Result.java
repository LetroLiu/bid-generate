package cn.letro.bid.generator.base.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 接口返回数据格式
 * @author
 * @date
 */
@Data
@ApiModel(value = "接口返回对象", description = "接口返回对象")
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 成功标志
     */
    @ApiModelProperty(value = "成功标志")
    private boolean success = true;

    /**
     * 返回处理消息
     */
    @ApiModelProperty(value = "返回处理消息")
    private String message = "操作成功！";

    /**
     * 返回代码
     */
    @ApiModelProperty(value = "返回代码")
    private String code = "0";

    /**
     * 返回数据对象 data
     */
    @ApiModelProperty(value = "返回数据对象")
    private T result;

    /**
     * 时间戳
     */
    @ApiModelProperty(value = "时间戳")
    private long timestamp = System.currentTimeMillis();

    /**
     * 错误集合
     */
    @ApiModelProperty(value = "错误集合")
    private List<Error> errors;

    public Result() {}

    /**
     * 处理成功
     */
    public boolean isSuccess(){
        return success;
    }

    /**
     * 处理失败
     */
    public boolean isFail(){
        return !isSuccess();
    }

    @Deprecated
    public Result<T> success(String message) {
        this.message = message;
        this.code = "200";
        this.success = true;
        return this;
    }

    @Deprecated
    public static Result<Object> ok() {
        Result<Object> r = new Result<Object>();
        r.setSuccess(true);
        r.setCode("200");
        return r;
    }

    @Deprecated
    public static Result<Object> ok(String msg) {
        Result<Object> r = new Result<Object>();
        r.setSuccess(true);
        r.setCode("200");
        r.setMessage(msg);
        return r;
    }

    @Deprecated
    public static Result<Object> ok(Object data) {
        Result<Object> r = new Result<Object>();
        r.setSuccess(true);
        r.setCode("200");
        r.setResult(data);
        return r;
    }

    public static <T> Result<T> OK() {
        Result<T> r = new Result<T>();
        r.setSuccess(true);
        r.setCode("200");
        return r;
    }

    public static <T> Result<T> OK(T data) {
        Result<T> r = new Result<T>();
        r.setSuccess(true);
        r.setCode("200");
        r.setResult(data);
        return r;
    }

    public static <T> Result<T> OK(String msg, T data) {
        Result<T> r = new Result<T>();
        r.setSuccess(true);
        r.setCode("200");
        r.setMessage(msg);
        r.setResult(data);
        return r;
    }

    public static Result<Object> error(String msg) {
        return error("500", msg);
    }

    public static Result<Object> error(String code, String msg) {
        Result<Object> r = new Result<Object>();
        r.setCode(code);
        r.setMessage(msg);
        r.setSuccess(false);
        return r;
    }

    public static Result<Object> error(String code, String msg, List<Error> errors) {
        Result<Object> r = new Result<Object>();
        r.setCode(code);
        r.setMessage(msg);
        r.setSuccess(false);
        r.setErrors(errors);
        return r;
    }

    public Result<T> error500(String message) {
        this.message = message;
        this.code = "500";
        this.success = false;
        return this;
    }

    public static <T> Result<T> error(String code, T data) {
        Result<T> r = new Result<T>();
        r.setCode(code);
        r.setSuccess(false);
        r.setResult(data);
        return r;
    }

    public static <T> Result<T> error(String code, String message, T data) {
        Result<T> r = new Result<T>();
        r.setCode(code);
        r.setSuccess(false);
        r.setResult(data);
        r.setMessage(message);
        return r;
    }
}
