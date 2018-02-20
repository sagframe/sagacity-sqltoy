package sqltoy.showcase.system.vo;

import org.sagacity.sqltoy.model.PaginationModel;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;


/**
 * @author xuan  <a href="zhangshixuanj@163.com">联系作者</a>
 * @version Revision:v1.0,Date: 2018/1/5 19:08
 * @project sinochem-parent
 * @description
 * @Modification Date: 2018/1/5 19:08  {填写修改说明}
 */
public class GoodsParam implements Serializable {
    /**
     * 搜索关键字
     */
    private String keyword;

    /**
     * 分页条件
     */
    private PaginationModel page;

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
     * 开始价格
     */
    private BigDecimal startPrice;

    /**
     * 结束价格
     */
    private BigDecimal endPrice;

    /**
     * 根据计量单位
     */
    private String uom;

    /**
     * 分类
     */
    private String goodsCateId;


    /**
     * 查询所有分类
     */
    private List<String> goodsCateIds;

    /**
     * 父级类类目id
     */
    private String goodsCatePid;

    /**
     * 品牌id
     */
    private String trademarkId;

    /**
     * 期货类型
     */
    private Integer transactionType;

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

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public PaginationModel getPage() {
        return page;
    }

    public void setPage(PaginationModel page) {
        this.page = page;
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

    public String getUom() {
        return uom;
    }

    public void setUom(String uom) {
        this.uom = uom;
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

    public String getTrademarkId() {
        return trademarkId;
    }

    public void setTrademarkId(String trademarkId) {
        this.trademarkId = trademarkId;
    }

    public Integer getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(Integer transactionType) {
        this.transactionType = transactionType;
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