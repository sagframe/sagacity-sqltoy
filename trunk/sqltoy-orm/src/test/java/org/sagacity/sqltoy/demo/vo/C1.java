package org.sagacity.sqltoy.demo.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Accessors(chain = true)
public class C1 implements Serializable {
    private int intNum;
    private Integer integerNum;
    private String str;
    private BigDecimal decimal;
    private short shortNum;
    private Short shortTNum;
    private Date date;
}
