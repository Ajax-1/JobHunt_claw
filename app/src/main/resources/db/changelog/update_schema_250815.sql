-- 新增公司行业
alter table draft_oc add column company_industry varchar(128) not null default '' comment '公司行业';
alter table oc_info add column company_industry varchar(128) not null default '' comment '公司行业';