/*==============================================================*/
/* Table: SQLTOY_COMPLEXPK_HEAD                                 */
/*==============================================================*/
create table SQLTOY_COMPLEXPK_HEAD
(
   TRANS_DATE           date not null  comment '交易日期',
   TRANS_CODE           varchar(30) not null  comment '业务代码',
   TOTAL_CNT            decimal(12,3) not null  comment '总数量',
   TOTAL_AMT            decimal(12,3) not null  comment '总金额',
   CREATE_BY            varchar(22) not null  comment '创建人',
   CREATE_TIME          datetime not null  comment '创建时间',
   UPDATE_BY            varchar(22) not null  comment '最后修改人',
   UPDATE_TIME          datetime not null  comment '最后修改时间',
   primary key (TRANS_DATE, TRANS_CODE)
);

alter table SQLTOY_COMPLEXPK_HEAD comment '复合主键级联操作主表';

/*==============================================================*/
/* Table: SQLTOY_COMPLEXPK_ITEM                                 */
/*==============================================================*/
create table SQLTOY_COMPLEXPK_ITEM
(
   ID                   varchar(32) not null  comment 'ID',
   TRANS_DATE           date  comment '交易日期',
   TRANS_ID             varchar(30)  comment '业务代码',
   PRODUCT_ID           varchar(32) not null  comment '商品编码',
   QUANTITY             decimal(8,3) not null  comment '数量',
   PRICE                decimal(8,3) not null  comment '价格',
   AMT                  decimal(10,3) not null  comment '总金额',
   CREATE_TIME          datetime not null  comment '创建时间',
   primary key (ID)
);

alter table SQLTOY_COMPLEXPK_ITEM comment '复合主键级联操作子表';

/*==============================================================*/
/* Table: SQLTOY_DEVICE_ORDER                                   */
/*==============================================================*/
create table SQLTOY_DEVICE_ORDER
(
   ORDER_ID             varchar(22) not null  comment '订单ID',
   DEVICE_TYPE          varchar(10) not null  comment '设备类型',
   PS_TYPE              varchar(10) not null  comment '购销标志',
   TOTAL_CNT            decimal(12,3)  comment '商品总量',
   TOTAL_AMT            decimal(12,2)  comment '总金额',
   BUYER                varchar(22)  comment '购买方',
   SALER                varchar(22)  comment '销售方',
   TRANS_DATE           date not null  comment '成交日期',
   DELIVERY_TERM        date  comment '交货期限',
   STAFF_ID             varchar(22)  comment '业务员',
   ORGAN_ID             varchar(22)  comment '业务机构',
   STATUS               int(1) not null  comment '状态',
   CREATE_BY            varchar(22) not null  comment '创建人',
   CREATE_TIME          datetime not null  comment '创建时间',
   UPDATE_BY            varchar(22) not null  comment '最后修改人',
   UPDATE_TIME          datetime not null  comment '最后修改时间',
   primary key (ORDER_ID)
);

alter table SQLTOY_DEVICE_ORDER comment '硬件购销定单表(演示有规则单号)';

/*==============================================================*/
/* Table: SQLTOY_DICT_DETAIL                                    */
/*==============================================================*/
create table SQLTOY_DICT_DETAIL
(
   DICT_KEY             varchar(50) not null  comment '字典KEY',
   DICT_TYPE            varchar(50) not null  comment '字典类型代码',
   DICT_NAME            varchar(200) not null  comment '字典值',
   SHOW_INDEX           numeric(8) not null default 1  comment '显示顺序',
   UPDATE_BY            varchar(22) not null  comment '最后修改人',
   UPDATE_TIME          datetime not null  comment '最后修改时间',
   STATUS               decimal(1) not null default 1  comment '状态',
   primary key (DICT_KEY, DICT_TYPE)
);

alter table SQLTOY_DICT_DETAIL comment '字典明细表';

/*==============================================================*/
/* Table: SQLTOY_DICT_TYPE                                      */
/*==============================================================*/
create table SQLTOY_DICT_TYPE
(
   DICT_TYPE            varchar(50) not null  comment '字典类型代码',
   DICT_TYPE_NAME       varchar(100) not null  comment '字典类型名称',
   COMMENTS             varchar(500)  comment '说明',
   SHOW_INDEX           numeric(8) not null default 1  comment '显示顺序',
   CREATE_BY            varchar(22) not null  comment '创建人',
   CREATE_TIME          datetime not null  comment '创建时间',
   UPDATE_BY            varchar(22) not null  comment '最后修改人',
   UPDATE_TIME          datetime not null  comment '最后修改时间',
   STATUS               decimal(1) not null default 1  comment '状态',
   primary key (DICT_TYPE)
);

alter table SQLTOY_DICT_TYPE comment '字典分类表';

/*==============================================================*/
/* Table: SQLTOY_FRUIT_ORDER                                    */
/*==============================================================*/
create table SQLTOY_FRUIT_ORDER
(
   FRUIT_NAME           varchar(100) not null  comment '水果名称',
   ORDER_MONTH          integer not null  comment '订单月份',
   SALE_COUNT           numeric(10,2) not null  comment '销售数量',
   SALE_PRICE           numeric(10,2) not null  comment '销售单价',
   TOTAL_AMT            numeric(10,2) not null  comment '总金额',
   primary key (FRUIT_NAME, ORDER_MONTH)
);

alter table SQLTOY_FRUIT_ORDER comment '查询汇总演示-水果订单表';

/*==============================================================*/
/* Table: SQLTOY_ORGAN_INFO                                     */
/*==============================================================*/
create table SQLTOY_ORGAN_INFO
(
   ORGAN_ID             varchar(22) not null  comment '机构ID',
   ORGAN_NAME           varchar(100) not null  comment '机构名称',
   ORGAN_CODE           varchar(20) not null  comment '机构代码',
   COST_NO              varchar(20)  comment '成本中心代码',
   ORGAN_PID            varchar(22) not null  comment '父机构ID',
   NODE_ROUTE           varchar(200)  comment '节点路径',
   NODE_LEVEL           numeric(1)  comment '节点等级',
   IS_LEAF              numeric(1)  comment '是否叶子节点',
   SHOW_INDEX           numeric(8) not null default 1  comment '显示顺序',
   CREATE_BY            varchar(22) not null  comment '创建人',
   CREATE_TIME          datetime not null  comment '创建时间',
   UPDATE_BY            varchar(22) not null  comment '最后修改人',
   UPDATE_TIME          datetime not null  comment '最后修改时间',
   STATUS               decimal(1) not null default 1  comment '状态',
   primary key (ORGAN_ID)
);

alter table SQLTOY_ORGAN_INFO comment '机构信息表';

/*==============================================================*/
/* Table: SQLTOY_STAFF_INFO                                     */
/*==============================================================*/
create table SQLTOY_STAFF_INFO
(
   STAFF_ID             varchar(22) not null  comment '员工ID',
   STAFF_CODE           varchar(22) not null  comment '工号',
   STAFF_NAME           varchar(30) not null  comment '姓名',
   ORGAN_ID             varchar(22) not null  comment '部门',
   SEX_TYPE             char(1) not null  comment '性别',
   BIRTHDAY             date  comment '出生日期',
   ENTRY_DATE           date not null  comment '入职日期',
   TERM_DATE            date  comment '离职日期',
   PHOTO                longblob  comment '照片',
   COUNTRY              varchar(10)  comment '国家',
   CENSUS_REGISTER      varchar(150)  comment '籍贯',
   ADDRESS              varchar(250)  comment '家庭地址',
   EMAIL                varchar(100)  comment '邮箱',
   TEL_NO               varchar(20)  comment '移动电话',
   POST                 varchar(20)  comment '岗位',
   POST_GRADE           varchar(20)  comment '职位级别',
   CREATE_BY            varchar(22) not null  comment '创建人',
   CREATE_TIME          datetime not null  comment '创建时间',
   UPDATE_BY            varchar(22) not null  comment '最后修改人',
   UPDATE_TIME          datetime not null  comment '最后修改时间',
   STATUS               decimal(1) not null default 1  comment '状态',
   primary key (STAFF_ID)
);

alter table SQLTOY_STAFF_INFO comment '员工信息表';

/*==============================================================*/
/* Table: SQLTOY_TRANS_INFO_15D                                 */
/*==============================================================*/
create table SQLTOY_TRANS_INFO_15D
(
   TRANS_ID             varchar(32) not null  comment '交易ID',
   TRANS_CODE           varchar(20) not null  comment '交易代码',
   TRANS_CHANNEL        varchar(20) not null  comment '交易渠道',
   TRANS_AMT            decimal(14,2) not null  comment '交易金额',
   STATUS               decimal(1) not null  comment '交易状态',
   RESULT_CODE          varchar(20) not null  comment '交易返回码',
   TRANS_TIME           datetime not null  comment '交易时间',
   TRANS_DATE           date not null  comment '交易日期',
   USER_ID              varchar(32) not null  comment '用户ID',
   CARD_NO              varchar(32)  comment '交易卡号',
   primary key (TRANS_ID)
);

alter table SQLTOY_TRANS_INFO_15D comment '支付交易流水表(15天表)';

/*==============================================================*/
/* Table: SQLTOY_TRANS_INFO_HIS                                 */
/*==============================================================*/
create table SQLTOY_TRANS_INFO_HIS
(
   TRANS_ID             varchar(32) not null  comment '交易ID',
   TRANS_CODE           varchar(20) not null  comment '交易代码',
   TRANS_CHANNEL        varchar(20) not null  comment '交易渠道',
   TRANS_AMT            decimal(14,2) not null  comment '交易金额',
   STATUS               decimal(1) not null  comment '交易状态',
   RESULT_CODE          varchar(20) not null  comment '交易返回码',
   TRANS_TIME           datetime not null  comment '交易时间',
   TRANS_DATE           date not null  comment '交易日期',
   USER_ID              varchar(32) not null  comment '用户ID',
   CARD_NO              varchar(32)  comment '交易卡号',
   primary key (TRANS_ID)
);

alter table SQLTOY_TRANS_INFO_HIS comment '支付交易流水表';

alter table SQLTOY_COMPLEXPK_ITEM add constraint FK_COMPLEXH_REF_ITEM foreign key (TRANS_DATE, TRANS_ID)
      references SQLTOY_COMPLEXPK_HEAD (TRANS_DATE, TRANS_CODE) on delete restrict on update restrict;

alter table SQLTOY_DICT_DETAIL add constraint FK_DICT_TYPE_REF_ITEM foreign key (DICT_TYPE)
      references SQLTOY_DICT_TYPE (DICT_TYPE) on delete restrict on update restrict;
      
INSERT INTO SQLTOY_DICT_TYPE 
(`DICT_TYPE`, `DICT_TYPE_NAME`, `COMMENTS`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('DEVICE_TYPE', '设备类型', '设备类型', 1, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_TYPE
(`DICT_TYPE`, `DICT_TYPE_NAME`, `COMMENTS`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('ORDER_STATUS', '订单状态', '订单状态', 3, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_TYPE
(`DICT_TYPE`, `DICT_TYPE_NAME`, `COMMENTS`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('POST_LEVEL', '岗位级别', '岗位级别', 5, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_TYPE
(`DICT_TYPE`, `DICT_TYPE_NAME`, `COMMENTS`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('POST_TYPE', '岗位类别', '岗位类别', 4, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_TYPE
(`DICT_TYPE`, `DICT_TYPE_NAME`, `COMMENTS`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('PURCHASE_SALE_TYPE', '采购销售分类', '采购销售分类', 2, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_TYPE
(`DICT_TYPE`, `DICT_TYPE_NAME`, `COMMENTS`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('SEX_TYPE', '性别', '性别', 6, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_TYPE
(`DICT_TYPE`, `DICT_TYPE_NAME`, `COMMENTS`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('TRANS_CHANNEL', '交易渠道', '交易渠道', 7, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_TYPE
(`DICT_TYPE`, `DICT_TYPE_NAME`, `COMMENTS`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('TRANS_CODE', '交易代码表', '交易代码表', 8, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);


INSERT INTO SQLTOY_DICT_DETAIL  
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('OFFICE', 'DEVICE_TYPE', '办公用品', 3, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('NET', 'DEVICE_TYPE', '网络设备', 2, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('OT', 'DEVICE_TYPE', '其他', 5, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('SOFTWARE', 'DEVICE_TYPE', '软件', 4, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('PC', 'DEVICE_TYPE', '个人电脑', 1, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('1', 'ORDER_STATUS', '编辑', 2, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('0', 'ORDER_STATUS', '作废', 1, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('3', 'ORDER_STATUS', '已生效', 4, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('2', 'ORDER_STATUS', '待审核', 3, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('L1', 'POST_LEVEL', 'L1', 1, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('L10', 'POST_LEVEL', 'L10', 10, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('L2', 'POST_LEVEL', 'L2', 2, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('L3', 'POST_LEVEL', 'L3', 3, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('L4', 'POST_LEVEL', 'L4', 4, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('L5', 'POST_LEVEL', 'L5', 5, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('L6', 'POST_LEVEL', 'L6', 6, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('L7', 'POST_LEVEL', 'L7', 7, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('L8', 'POST_LEVEL', 'L8', 8, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('L9', 'POST_LEVEL', 'L9', 9, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('T', 'POST_TYPE', '技术岗', 2, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('M', 'POST_TYPE', '管理岗', 1, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('O', 'POST_TYPE', '其他', 4, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('F', 'POST_TYPE', '职能岗', 3, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('PO', 'PURCHASE_SALE_TYPE', '采购', 1, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('SO', 'PURCHASE_SALE_TYPE', '销售', 2, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('M', 'SEX_TYPE', '男', 2, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('F', 'SEX_TYPE', '女', 1, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('JD', 'TRANS_CHANNEL', '京东', 1, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('SHOP', 'TRANS_CHANNEL', '线下门店', 3, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('TIANMAO', 'TRANS_CHANNEL', '天猫', 2, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('T001', 'TRANS_CODE', '下单', 1, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('T002', 'TRANS_CODE', '撤销', 2, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('T003', 'TRANS_CODE', '付款', 3, 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_DICT_DETAIL
(`DICT_KEY`, `DICT_TYPE`, `DICT_NAME`, `SHOW_INDEX`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('T004', 'TRANS_CODE', '订单查询', 4, 'S0001', '2019-08-01 16:47:01.000', 1);


INSERT INTO SQLTOY_ORGAN_INFO 
(`ORGAN_ID`, `ORGAN_NAME`, `ORGAN_CODE`, `COST_NO`, `ORGAN_PID`, `NODE_ROUTE`, `NODE_LEVEL`, `IS_LEAF`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('100001', 'X科技创新集团有限公司', '100001', NULL, '-1', '-1,100001,', 1, 0, 1, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_ORGAN_INFO
(`ORGAN_ID`, `ORGAN_NAME`, `ORGAN_CODE`, `COST_NO`, `ORGAN_PID`, `NODE_ROUTE`, `NODE_LEVEL`, `IS_LEAF`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('100002', '总经理办公室', '100002', NULL, '100001', '-1,100001,100002,', 2, 1, 2, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_ORGAN_INFO
(`ORGAN_ID`, `ORGAN_NAME`, `ORGAN_CODE`, `COST_NO`, `ORGAN_PID`, `NODE_ROUTE`, `NODE_LEVEL`, `IS_LEAF`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('100003', '人力资源部', '100003', NULL, '100001', '-1,100001,100003,', 2, 1, 3, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_ORGAN_INFO
(`ORGAN_ID`, `ORGAN_NAME`, `ORGAN_CODE`, `COST_NO`, `ORGAN_PID`, `NODE_ROUTE`, `NODE_LEVEL`, `IS_LEAF`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('100004', '财务部', '100004', NULL, '100001', '-1,100001,100004,', 2, 1, 4, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_ORGAN_INFO
(`ORGAN_ID`, `ORGAN_NAME`, `ORGAN_CODE`, `COST_NO`, `ORGAN_PID`, `NODE_ROUTE`, `NODE_LEVEL`, `IS_LEAF`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('100005', '生物医药研发中心', '100005', NULL, '100001', '-1,100001,100005,', 2, 1, 5, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_ORGAN_INFO
(`ORGAN_ID`, `ORGAN_NAME`, `ORGAN_CODE`, `COST_NO`, `ORGAN_PID`, `NODE_ROUTE`, `NODE_LEVEL`, `IS_LEAF`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('100006', '智能设备研发中心', '100006', NULL, '100001', '-1,100001,100006,', 2, 1, 6, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_ORGAN_INFO
(`ORGAN_ID`, `ORGAN_NAME`, `ORGAN_CODE`, `COST_NO`, `ORGAN_PID`, `NODE_ROUTE`, `NODE_LEVEL`, `IS_LEAF`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('100007', '信息化研发中心', '100007', NULL, '100001', '-1,100001,100007,', 2, 1, 7, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_ORGAN_INFO
(`ORGAN_ID`, `ORGAN_NAME`, `ORGAN_CODE`, `COST_NO`, `ORGAN_PID`, `NODE_ROUTE`, `NODE_LEVEL`, `IS_LEAF`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('100008', '新动力研发中心', '100008', NULL, '100001', '-1,100001,100008,', 2, 0, 8, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_ORGAN_INFO
(`ORGAN_ID`, `ORGAN_NAME`, `ORGAN_CODE`, `COST_NO`, `ORGAN_PID`, `NODE_ROUTE`, `NODE_LEVEL`, `IS_LEAF`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('100009', '新能源研究院', '100009', NULL, '100008', '-1,100001,100008,100009,', 3, 1, 9, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_ORGAN_INFO
(`ORGAN_ID`, `ORGAN_NAME`, `ORGAN_CODE`, `COST_NO`, `ORGAN_PID`, `NODE_ROUTE`, `NODE_LEVEL`, `IS_LEAF`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('100010', '发动机研究院', '100010', NULL, '100008', '-1,100001,100008,100010,', 3, 1, 10, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);
INSERT INTO SQLTOY_ORGAN_INFO
(`ORGAN_ID`, `ORGAN_NAME`, `ORGAN_CODE`, `COST_NO`, `ORGAN_PID`, `NODE_ROUTE`, `NODE_LEVEL`, `IS_LEAF`, `SHOW_INDEX`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('100011', '后勤保障部', '100011', NULL, '100001', '-1,100001,100011,', 2, 1, 11, 'S0001', '2019-08-01 16:47:01.000', 'S0001', '2019-08-01 16:47:01.000', 1);


INSERT INTO SQLTOY_STAFF_INFO  
(`STAFF_ID`, `STAFF_CODE`, `STAFF_NAME`, `ORGAN_ID`, `SEX_TYPE`, `BIRTHDAY`, `ENTRY_DATE`, `TERM_DATE`, `PHOTO`, `COUNTRY`, `CENSUS_REGISTER`, `ADDRESS`, `EMAIL`, `TEL_NO`, `POST`, `POST_GRADE`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('S0001', 'S0001', '张三', '100002', 'M', NULL, '2019-08-01', NULL, NULL, '86', NULL, '上海市黄浦区三大路254号402室', NULL, '13887981123', 'M', 'L10', 'S0001', '2019-07-31 11:04:11.000', 'S0001', '2019-07-31 11:04:11.000', 1);
INSERT INTO SQLTOY_STAFF_INFO
(`STAFF_ID`, `STAFF_CODE`, `STAFF_NAME`, `ORGAN_ID`, `SEX_TYPE`, `BIRTHDAY`, `ENTRY_DATE`, `TERM_DATE`, `PHOTO`, `COUNTRY`, `CENSUS_REGISTER`, `ADDRESS`, `EMAIL`, `TEL_NO`, `POST`, `POST_GRADE`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('S0002', 'S0002', '李四', '100003', 'M', NULL, '2019-08-01', NULL, NULL, '86', NULL, '上海市黄浦区三大路254号402室', NULL, '13987988765', 'M', 'L5', 'S0001', '2019-07-31 11:19:31.000', 'S0001', '2019-07-31 11:19:31.000', 1);
INSERT INTO SQLTOY_STAFF_INFO
(`STAFF_ID`, `STAFF_CODE`, `STAFF_NAME`, `ORGAN_ID`, `SEX_TYPE`, `BIRTHDAY`, `ENTRY_DATE`, `TERM_DATE`, `PHOTO`, `COUNTRY`, `CENSUS_REGISTER`, `ADDRESS`, `EMAIL`, `TEL_NO`, `POST`, `POST_GRADE`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('S0003', 'S0003', '陈美凤', '100004', 'F', NULL, '2019-08-01', NULL, NULL, '86', NULL, '上海市黄浦区三大路254号402室', NULL, '13947981725', 'M', 'L5', 'S0001', '2019-07-31 11:19:31.000', 'S0001', '2019-07-31 11:19:31.000', 1);
INSERT INTO SQLTOY_STAFF_INFO
(`STAFF_ID`, `STAFF_CODE`, `STAFF_NAME`, `ORGAN_ID`, `SEX_TYPE`, `BIRTHDAY`, `ENTRY_DATE`, `TERM_DATE`, `PHOTO`, `COUNTRY`, `CENSUS_REGISTER`, `ADDRESS`, `EMAIL`, `TEL_NO`, `POST`, `POST_GRADE`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('S0004', 'S0004', '张秀娟', '100005', 'F', NULL, '2019-08-01', NULL, NULL, '86', NULL, '上海市黄浦区三大路254号402室', NULL, '13927958763', 'M', 'L5', 'S0001', '2019-07-31 11:19:31.000', 'S0001', '2019-07-31 11:19:31.000', 1);
INSERT INTO SQLTOY_STAFF_INFO
(`STAFF_ID`, `STAFF_CODE`, `STAFF_NAME`, `ORGAN_ID`, `SEX_TYPE`, `BIRTHDAY`, `ENTRY_DATE`, `TERM_DATE`, `PHOTO`, `COUNTRY`, `CENSUS_REGISTER`, `ADDRESS`, `EMAIL`, `TEL_NO`, `POST`, `POST_GRADE`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('S0005', 'S0005', '王伟', '100006', 'M', NULL, '2019-08-01', NULL, NULL, '86', NULL, '上海市黄浦区三大路254号402室', NULL, '13987972765', 'M', 'L5', 'S0001', '2019-07-31 11:19:31.000', 'S0001', '2019-07-31 11:19:31.000', 1);
INSERT INTO SQLTOY_STAFF_INFO
(`STAFF_ID`, `STAFF_CODE`, `STAFF_NAME`, `ORGAN_ID`, `SEX_TYPE`, `BIRTHDAY`, `ENTRY_DATE`, `TERM_DATE`, `PHOTO`, `COUNTRY`, `CENSUS_REGISTER`, `ADDRESS`, `EMAIL`, `TEL_NO`, `POST`, `POST_GRADE`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('S0006', 'S0006', '陈一鸣', '100007', 'M', NULL, '2019-08-01', NULL, NULL, '86', NULL, '上海市黄浦区三大路254号402室', NULL, '13987988185', 'M', 'L5', 'S0001', '2019-07-31 11:19:31.000', 'S0001', '2019-07-31 11:19:31.000', 1);
INSERT INTO SQLTOY_STAFF_INFO
(`STAFF_ID`, `STAFF_CODE`, `STAFF_NAME`, `ORGAN_ID`, `SEX_TYPE`, `BIRTHDAY`, `ENTRY_DATE`, `TERM_DATE`, `PHOTO`, `COUNTRY`, `CENSUS_REGISTER`, `ADDRESS`, `EMAIL`, `TEL_NO`, `POST`, `POST_GRADE`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('S0007', 'S0007', '彭越', '100008', 'M', NULL, '2019-08-01', NULL, NULL, '86', NULL, '上海市黄浦区三大路254号402室', NULL, '13987988365', 'M', 'L5', 'S0001', '2019-07-31 11:19:31.000', 'S0001', '2019-07-31 11:19:31.000', 1);
INSERT INTO SQLTOY_STAFF_INFO
(`STAFF_ID`, `STAFF_CODE`, `STAFF_NAME`, `ORGAN_ID`, `SEX_TYPE`, `BIRTHDAY`, `ENTRY_DATE`, `TERM_DATE`, `PHOTO`, `COUNTRY`, `CENSUS_REGISTER`, `ADDRESS`, `EMAIL`, `TEL_NO`, `POST`, `POST_GRADE`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('S0008', 'S0008', '郑成功', '100009', 'M', NULL, '2019-08-01', NULL, NULL, '86', NULL, '上海市黄浦区三大路254号402室', NULL, '13987988715', 'M', 'L5', 'S0001', '2019-07-31 11:19:31.000', 'S0001', '2019-07-31 11:19:31.000', 1);
INSERT INTO SQLTOY_STAFF_INFO
(`STAFF_ID`, `STAFF_CODE`, `STAFF_NAME`, `ORGAN_ID`, `SEX_TYPE`, `BIRTHDAY`, `ENTRY_DATE`, `TERM_DATE`, `PHOTO`, `COUNTRY`, `CENSUS_REGISTER`, `ADDRESS`, `EMAIL`, `TEL_NO`, `POST`, `POST_GRADE`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('S0009', 'S0009', '张大海', '100010', 'M', NULL, '2019-08-01', NULL, NULL, '86', NULL, '上海市黄浦区三大路254号402室', NULL, '13987988725', 'M', 'L5', 'S0001', '2019-07-31 11:19:31.000', 'S0001', '2019-07-31 11:19:31.000', 1);
INSERT INTO SQLTOY_STAFF_INFO
(`STAFF_ID`, `STAFF_CODE`, `STAFF_NAME`, `ORGAN_ID`, `SEX_TYPE`, `BIRTHDAY`, `ENTRY_DATE`, `TERM_DATE`, `PHOTO`, `COUNTRY`, `CENSUS_REGISTER`, `ADDRESS`, `EMAIL`, `TEL_NO`, `POST`, `POST_GRADE`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('S0010', 'S0010', '汪涛', '100011', 'M', NULL, '2019-08-01', NULL, NULL, '86', NULL, '上海市黄浦区三大路254号402室', NULL, '13987988766', 'M', 'L5', 'S0001', '2019-07-31 11:19:31.000', 'S0001', '2019-07-31 11:19:31.000', 1);
INSERT INTO SQLTOY_STAFF_INFO
(`STAFF_ID`, `STAFF_CODE`, `STAFF_NAME`, `ORGAN_ID`, `SEX_TYPE`, `BIRTHDAY`, `ENTRY_DATE`, `TERM_DATE`, `PHOTO`, `COUNTRY`, `CENSUS_REGISTER`, `ADDRESS`, `EMAIL`, `TEL_NO`, `POST`, `POST_GRADE`, `CREATE_BY`, `CREATE_TIME`, `UPDATE_BY`, `UPDATE_TIME`, `STATUS`)
VALUES('S0012', 'S0012', '陈大鹏', '100007', 'M', NULL, '2019-08-01', NULL, NULL, '86', NULL, '上海市黄浦区三大路254号402室', NULL, '13987488762', 'M', 'L5', 'S0001', '2019-07-31 11:19:31.000', 'S0001', '2019-07-31 11:19:31.000', 0);

INSERT INTO SQLTOY_FRUIT_ORDER  
(FRUIT_NAME, ORDER_MONTH, SALE_COUNT, SALE_PRICE, TOTAL_AMT)
VALUES('香蕉', 202005, 10, 2000, 2000);
INSERT INTO SQLTOY_FRUIT_ORDER
(FRUIT_NAME, ORDER_MONTH, SALE_COUNT, SALE_PRICE, TOTAL_AMT)
VALUES('香蕉', 202004, 12, 2400, 2700);
INSERT INTO SQLTOY_FRUIT_ORDER
(FRUIT_NAME, ORDER_MONTH, SALE_COUNT, SALE_PRICE, TOTAL_AMT)
VALUES('香蕉', 202003, 13, 2300, 2700);
INSERT INTO SQLTOY_FRUIT_ORDER
(FRUIT_NAME, ORDER_MONTH, SALE_COUNT, SALE_PRICE, TOTAL_AMT)
VALUES('苹果', 202005, 12, 2000, 2400);
INSERT INTO SQLTOY_FRUIT_ORDER
(FRUIT_NAME, ORDER_MONTH, SALE_COUNT, SALE_PRICE, TOTAL_AMT)
VALUES('苹果', 202004, 11, 1900, 2600);
INSERT INTO SQLTOY_FRUIT_ORDER
(FRUIT_NAME, ORDER_MONTH, SALE_COUNT, SALE_PRICE, TOTAL_AMT)
VALUES('苹果', 202003, 13, 2000, 2500);
