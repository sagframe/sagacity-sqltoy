package org.sagacity.sqltoy.demo.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
/**
 * 主子表级联演示实体B1
 */
public class B1 implements Serializable {
	private int intNum;
	private Integer integerNum;
	private String str;
	private BigDecimal decimal;
	private short shortNum;
	private Short shortTNum;
	private Date date;
}
