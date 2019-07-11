/*==============================================================*/
/* DBMS name:      MySQL 5.0                                    */
/* Created on:     2017/3/20 15:25:57                           */
/*==============================================================*/


drop table if exists SAG_DICT_DETAIL;

drop index IDX_SAG_DICTTYPE_NAME on SAG_DICT_TYPE;

drop table if exists SAG_DICT_TYPE;

drop table if exists SYS_AREA_CODE;

drop table if exists SYS_BIG_LOB;

drop table if exists SYS_MULTPK_ITEM;

drop table if exists SYS_MULTPK_MAIN;

drop index IDX_ORGAN_NODE_ROUTE on SYS_ORGAN_INFO;

drop table if exists SYS_ORGAN_INFO;

drop table if exists SYS_SHARDING_HIS;

drop table if exists SYS_SHARDING_REAL;

drop index IDX_STAFF_ORGAN on SYS_STAFF_INFO;

drop table if exists SYS_STAFF_INFO;

drop table if exists SYS_SUMMARY_CASE;

drop table if exists SYS_UNPIVOT_DATA;

/*==============================================================*/
/* Table: SAG_DICT_DETAIL                                       */
/*==============================================================*/
create table SAG_DICT_DETAIL
(
   DICT_TYPE_CODE       varchar(50) not null comment '字典类型编码',
   DICT_KEY             varchar(100) not null comment '字典KEY(字典编码)',
   DICT_NAME            varchar(100) not null comment '字典显示值(显示的名称)',
   SHOW_INDEX           decimal(8) not null comment '显示顺序',
   COMMENTS             varchar(1000) comment '字典描述',
   SEGMENT              varchar(100) comment '附加字段',
   UPDATE_BY            varchar(22) not null comment '最后修改人',
   UPDATE_TIME          TIMESTAMP not null comment '最后修改时间',
   STATUS               char(1) not null default '1' comment '启用标志',
   primary key (DICT_KEY, DICT_TYPE_CODE)
);

alter table SAG_DICT_DETAIL comment '数据字典明细表';

/*==============================================================*/
/* Table: SAG_DICT_TYPE                                         */
/*==============================================================*/
create table SAG_DICT_TYPE
(
   DICT_TYPE_CODE       varchar(50) not null comment '字典类型编码',
   DICT_TYPE_NAME       varchar(100) not null comment '字典类型名称',
   COMMENTS             varchar(500) comment '字典类型描述',
   DATA_SIZE            decimal(4) comment '字典KEY数据长度',
   DATA_TYPE            decimal(1) not null default 0 comment '字典KEY数据类型',
   OPERATOR             varchar(22) comment '修改人',
   OPERATE_DATE         date comment '修改日期',
   SEGMENT_NAME         varchar(100) comment '扩展属性1名称',
   SEGMENT_DICT_TYPE    varchar(50) comment '扩展属性来源字典',
   STATUS               char(1) not null default '1' comment '启用标志',
   primary key (DICT_TYPE_CODE)
);

alter table SAG_DICT_TYPE comment '数据字典分类表';

/*==============================================================*/
/* Index: IDX_SAG_DICTTYPE_NAME                                 */
/*==============================================================*/
create unique index IDX_SAG_DICTTYPE_NAME on SAG_DICT_TYPE
(
   DICT_TYPE_NAME
);

/*==============================================================*/
/* Table: SYS_AREA_CODE                                         */
/*==============================================================*/
create table SYS_AREA_CODE
(
   AREA_CODE            varchar(6) not null comment '地区码',
   AREA_NAME            varchar(40) not null comment '地区名称',
   CITY_CODE            varchar(6) comment '城市代码',
   CITY_NAME            varchar(30) comment '城市名称',
   PROVINCE_CODE        varchar(6) comment '省份代码',
   PROVINCE_NAME        varchar(30) comment '省份名称',
   SHOW_INDEX           integer not null comment '显示顺序',
   STATUS               char(1) not null default '1' comment '启用标志',
   primary key (AREA_CODE)
);

alter table SYS_AREA_CODE comment '地区代码表';

/*==============================================================*/
/* Table: SYS_BIG_LOB                                           */
/*==============================================================*/
create table SYS_BIG_LOB
(
   ID                   int not null auto_increment comment '主键',
   STAFF_NAME           VARCHAR(30) comment '员工姓名',
   CREATE_DATE          date not null comment '创建日期',
   COMMENTS             text comment '说明',
   STAFF_PHOTO          longblob comment '员工照片',
   primary key (ID)
);

alter table SYS_BIG_LOB comment '大数据类型存储';

/*==============================================================*/
/* Table: SYS_MULTPK_ITEM                                       */
/*==============================================================*/
create table SYS_MULTPK_ITEM
(
   GOODS_NO             varchar(20) not null comment '商品编号',
   BIZ_TYPE             varchar(20) comment '业务分类编号',
   BATCH_NO             varchar(10) comment '批次号',
   QUANTITY             decimal(4) not null comment '商品数量',
   PRICE                decimal(6,2) not null comment '单价',
   primary key (GOODS_NO)
);

alter table SYS_MULTPK_ITEM comment '复合主键关联子表';

/*==============================================================*/
/* Table: SYS_MULTPK_MAIN                                       */
/*==============================================================*/
create table SYS_MULTPK_MAIN
(
   BIZ_TYPE             varchar(20) not null comment '业务分类编号',
   BATCH_NO             varchar(10) not null comment '批次号',
   AMOUNT               decimal(12,2) not null comment '交易金额',
   CREATE_TIME          date not null comment '创建日期',
   primary key (BIZ_TYPE, BATCH_NO)
);

alter table SYS_MULTPK_MAIN comment '复合主键关联主表';

/*==============================================================*/
/* Table: SYS_ORGAN_INFO                                        */
/*==============================================================*/
create table SYS_ORGAN_INFO
(
   ORGAN_ID             VARCHAR(22) not null comment '机构编号',
   ORGAN_NAME           VARCHAR(100) not null comment '机构名称',
   ALIAS_NAME           VARCHAR(100) comment '机构简称',
   ORGAN_CODE           VARCHAR(60) comment '机构代码',
   ORGAN_PID            VARCHAR(22) comment '上级机构',
   NODE_ROUTE           VARCHAR(220) comment '节点路径',
   NODE_LEVEL           decimal(2) comment '节点层级',
   IS_LEAF              decimal(1) comment '是否叶子节点',
   OPERATOR             VARCHAR(22) not null comment '操作人',
   OPERATE_DATE         DATE not null comment '操作日期',
   STATUS               char(1) not null default '1' comment '启用标志',
   primary key (ORGAN_ID)
);

alter table SYS_ORGAN_INFO comment '机构信息表';

/*==============================================================*/
/* Index: IDX_ORGAN_NODE_ROUTE                                  */
/*==============================================================*/
create index IDX_ORGAN_NODE_ROUTE on SYS_ORGAN_INFO
(
   NODE_ROUTE
);

/*==============================================================*/
/* Table: SYS_SHARDING_HIS                                      */
/*==============================================================*/
create table SYS_SHARDING_HIS
(
   ID                   decimal(22) not null comment '编号',
   STAFF_ID             varchar(30) not null comment '员工工号',
   POST_TYPE            varchar(30) comment '岗位类别',
   CREATE_TIME          datetime not null comment '创建时间',
   COMMENTS             varchar(1000) comment '说明',
   primary key (ID)
);

alter table SYS_SHARDING_HIS comment '分片测试历史表';

/*==============================================================*/
/* Table: SYS_SHARDING_REAL                                     */
/*==============================================================*/
create table SYS_SHARDING_REAL
(
   ID                   decimal(22) not null comment '编号',
   STAFF_ID             varchar(30) not null comment '员工工号',
   POST_TYPE            varchar(30) comment '岗位类别',
   CREATE_TIME          datetime not null comment '创建时间',
   COMMENTS             varchar(1000) comment '说明',
   primary key (ID)
);

alter table SYS_SHARDING_REAL comment '分片测试实时表';

/*==============================================================*/
/* Table: SYS_STAFF_INFO                                        */
/*==============================================================*/
create table SYS_STAFF_INFO
(
   STAFF_ID             VARCHAR(22) not null comment '员工ID',
   STAFF_CODE           VARCHAR(22) not null comment '员工工号',
   ORGAN_ID             VARCHAR(22) not null comment '机构编号',
   STAFF_NAME           VARCHAR(60) not null comment '员工姓名',
   SEX_TYPE             CHAR(1) comment 'DD性别',
   MOBILE_TEL           VARCHAR(15) comment '联系电话',
   BIRTHDAY             DATE comment '出生日期',
   DUTY_DATE            DATE comment '入职日期',
   OUT_DUTY_DATE        DATE comment '离职日期',
   POST                 VARCHAR(6) comment 'DD岗位',
   NATIVE_PLACE         varchar(10) comment '籍贯',
   EMAIL                VARCHAR(100) comment '邮箱地址',
   OPERATOR             VARCHAR(22) not null comment '操作人',
   OPERATE_DATE         DATE not null comment '操作日期',
   STATUS               char(1) not null default '1' comment '启用标志',
   primary key (STAFF_ID)
);

alter table SYS_STAFF_INFO comment '员工信息表';

/*==============================================================*/
/* Index: IDX_STAFF_ORGAN                                       */
/*==============================================================*/
create index IDX_STAFF_ORGAN on SYS_STAFF_INFO
(
   ORGAN_ID
);

/*==============================================================*/
/* Table: SYS_SUMMARY_CASE                                      */
/*==============================================================*/
create table SYS_SUMMARY_CASE
(
   TRANS_ID             varchar(22) not null comment '交易流水号',
   TRANS_DATE           date not null comment '交易日期',
   TRANS_CHANNEL        varchar(20) not null comment '交易渠道',
   TRANS_CODE           varchar(20) comment '交易代码',
   TRANS_AMT            decimal(12,2) not null comment '交易金额',
   primary key (TRANS_ID)
);

alter table SYS_SUMMARY_CASE comment '汇总计算演示表';

/*==============================================================*/
/* Table: SYS_UNPIVOT_DATA                                      */
/*==============================================================*/
create table SYS_UNPIVOT_DATA
(
   TRANS_DATE           date not null comment '交易日期',
   TOTAL_AMOUNT         decimal(10,2) not null comment '交易总金额',
   PERSON_AMOUNT        decimal(10,2) not null comment '个人交易金额',
   COMPANY_AMOUNT       decimal(10,2) not null comment '企业交易金额',
   primary key (TRANS_DATE)
);

alter table SYS_UNPIVOT_DATA comment '行转列测试';

alter table SAG_DICT_DETAIL add constraint FK_DICT_REF_TYPE foreign key (DICT_TYPE_CODE)
      references SAG_DICT_TYPE (DICT_TYPE_CODE) on delete cascade on update restrict;

alter table SYS_MULTPK_ITEM add constraint FK_SAG_ITEM_REF_MAIN foreign key (BIZ_TYPE, BATCH_NO)
      references SYS_MULTPK_MAIN (BIZ_TYPE, BATCH_NO) on delete cascade on update restrict;

