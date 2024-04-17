package org.hotswap.agent.dto;

public class BaseResponse<T> {

    private String code;

    private String message;

    private T data;

    public BaseResponse() {
        this.code = "0";
        this.message = "success";
    }

    public BaseResponse(T data) {
        this.code = "0";
        this.message = "success";
        this.data = data;
    }

    public static <T> BaseResponse<T> build() {
        return new BaseResponse<>();
    }

    public static <T> BaseResponse<T> build(T data) {
        return new BaseResponse<>(data);
    }

    public static <T> BaseResponse<T> fail(T data) {
        BaseResponse<T> res = new BaseResponse<>(data);
        res.code = "-1";
        res.message = "failure";
        res.setData(data);
        return res;
    }

    public String getCode() {
        return code;
    }

    public String getMsg() {
        return message;
    }

    public T getData() {
        return data;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setData(T data) {
        this.data = data;
    }

    public boolean isSuccess() {
        return "0".equals(this.code);
    }


}
