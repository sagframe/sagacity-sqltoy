package org.sagacity.sqltoy.demo.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Accessors(chain = true)
public class A2 implements Serializable {
    private int intNum;
    private Integer integerNum;
    private String str;
    private BigDecimal decimal;
    private short shortNum;
    private Short shortTNum;
    private Date date;
    private B2 b;
    private List<C2> c;
}
