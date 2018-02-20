package sqltoy.showcase.system.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @author xuan  <a href="zhangshixuanj@163.com">联系作者</a>
 * @version Revision:v1.0,Date: 2018/1/31 9:52
 * @project sinochem-parent
 * @description
 * @Modification Date: 2018/1/31 9:52  {填写修改说明}
 */
public class Goods implements Serializable {
    /**
     * 资源id
     */
    private String resourceId;

    /**
     * 库存地址
     */
    private String address;

    /**
     * 资源名称
     */
    private String chineseName;

    /**
     * 货币类型
     */
    private Integer currencyType;

    /**
     * 支付方式
     */
    private Integer deliveryMode;

    /**
     * 企业id
     */
    private String enterpriseId;

    /**
     * 资源标价
     */
    private BigDecimal price;

    /**
     * 厂商id
     */
    private String manufacturerId;

    /**
     * 厂商名称
     */
    private String manufacturerName;

    /**
     * 品牌id
     */
    private String trademarkId;

    /**
     * 牌号名称
     */
    private String trademarkName;

    /**
     * 期货类型 交易类型【0-现货，1-期货，2-远期】
     */
    private Integer transactionType;

    /**
     * 期货天数
     */
    private String days;

    /**
     * 计量单位 【0-吨，1-千克，2-克】
     */
    private String uom;

    /**
     * 上架时间
     */
    private Date upTime;

    /**
     * 国家
     */
    private String country;

    /**
     * 省市查询
     */
    private String provinceCode;

    /**
     * 城市查询
     */
    private String cityCode;

    /**
     * 地区查询
     */
    private String areCode;

    /**
     * 分类
     */
    private String goodsCateId;

    /**
     * 父级类类目id
     */
    private String goodsCatePid;

    /**
     * 搜索关键字
     */
    private String keyword;
    /**
     * 搜索关键字
     */
    private String keyword2;
    /**
     * 搜索关键字
     */
    private String keyword3;
    /**
     * 开始价格
     */
    private BigDecimal startPrice;

    /**
     * 结束价格
     */
    private BigDecimal endPrice;

    /**
     * 如果是条数
     */
    private Integer listCount;

    /**
     * 期货排序
     */
    private Integer transactionSort;

    /**
     * 价格排序
     */
    private Integer priceSort;

    /**
     * 查询所有分类
     */
    private List<String> goodsCateIds;


    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getChineseName() {
        return chineseName;
    }

    public void setChineseName(String chineseName) {
        this.chineseName = chineseName;
    }

    public Integer getCurrencyType() {
        return currencyType;
    }

    public void setCurrencyType(Integer currencyType) {
        this.currencyType = currencyType;
    }

    public Integer getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(Integer deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public String getEnterpriseId() {
        return enterpriseId;
    }

    public void setEnterpriseId(String enterpriseId) {
        this.enterpriseId = enterpriseId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getManufacturerId() {
        return manufacturerId;
    }

    public void setManufacturerId(String manufacturerId) {
        this.manufacturerId = manufacturerId;
    }

    public String getManufacturerName() {
        return manufacturerName;
    }

    public void setManufacturerName(String manufacturerName) {
        this.manufacturerName = manufacturerName;
    }

    public String getTrademarkId() {
        return trademarkId;
    }

    public void setTrademarkId(String trademarkId) {
        this.trademarkId = trademarkId;
    }

    public String getTrademarkName() {
        return trademarkName;
    }

    public void setTrademarkName(String trademarkName) {
        this.trademarkName = trademarkName;
    }

    public Integer getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(Integer transactionType) {
        this.transactionType = transactionType;
    }

    public String getUom() {
        return uom;
    }

    public void setUom(String uom) {
        this.uom = uom;
    }

    public Date getUpTime() {
        return upTime;
    }

    public void setUpTime(Date upTime) {
        this.upTime = upTime;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getProvinceCode() {
        return provinceCode;
    }

    public void setProvinceCode(String provinceCode) {
        this.provinceCode = provinceCode;
    }

    public String getCityCode() {
        return cityCode;
    }

    public void setCityCode(String cityCode) {
        this.cityCode = cityCode;
    }

    public String getAreCode() {
        return areCode;
    }

    public void setAreCode(String areCode) {
        this.areCode = areCode;
    }

    public String getGoodsCateId() {
        return goodsCateId;
    }

    public void setGoodsCateId(String goodsCateId) {
        this.goodsCateId = goodsCateId;
    }

    public String getGoodsCatePid() {
        return goodsCatePid;
    }

    public void setGoodsCatePid(String goodsCatePid) {
        this.goodsCatePid = goodsCatePid;
    }

    public String getDays() {
        return days;
    }

    public void setDays(String days) {
        this.days = days;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getKeyword2() {
        return keyword2;
    }

    public void setKeyword2(String keyword2) {
        this.keyword2 = keyword2;
    }

    public String getKeyword3() {
        return keyword3;
    }

    public void setKeyword3(String keyword3) {
        this.keyword3 = keyword3;
    }

    public BigDecimal getStartPrice() {
        return startPrice;
    }

    public void setStartPrice(BigDecimal startPrice) {
        this.startPrice = startPrice;
    }

    public BigDecimal getEndPrice() {
        return endPrice;
    }

    public void setEndPrice(BigDecimal endPrice) {
        this.endPrice = endPrice;
    }

    public Integer getListCount() {
        return listCount;
    }

    public void setListCount(Integer listCount) {
        this.listCount = listCount;
    }

    public Integer getTransactionSort() {
        return transactionSort;
    }

    public void setTransactionSort(Integer transactionSort) {
        this.transactionSort = transactionSort;
    }

    public Integer getPriceSort() {
        return priceSort;
    }

    public void setPriceSort(Integer priceSort) {
        this.priceSort = priceSort;
    }

    public List<String> getGoodsCateIds() {
        return goodsCateIds;
    }

    public void setGoodsCateIds(List<String> goodsCateIds) {
        this.goodsCateIds = goodsCateIds;
    }
}
