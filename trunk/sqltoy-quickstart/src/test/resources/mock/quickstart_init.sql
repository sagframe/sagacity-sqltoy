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
   AMT                  decimal(14,2) not null  comment '交易金额',
   STATUS               decimal(1) not null  comment '交易状态',
   RESULT_CODE          varchar(20) not null  comment '交易返回码',
   TRANS_TIME           datetime not null  comment '交易时间',
   TRANS_DATE           date not null  comment '交易日期',
   USER_ID              varchar(32) not null  comment '用户ID',
   CARD_NO              varchar(32)  comment '交易卡号',
   primary key (TRANS_ID)
);

alter table SQLTOY_TRANS_INFO_HIS comment '支付交易流水表';

alter table SQLTOY_DICT_DETAIL add constraint FK_DICT_TYPE_REF_ITEM foreign key (DICT_TYPE)
      references SQLTOY_DICT_TYPE (DICT_TYPE) on delete restrict on update restrict;
