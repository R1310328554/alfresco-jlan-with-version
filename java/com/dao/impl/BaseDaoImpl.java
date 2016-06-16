package com.dao.impl;

import org.springframework.jdbc.core.JdbcTemplate;

import com.dao.BaseDao;

public class BaseDaoImpl implements BaseDao {
	private JdbcTemplate jdbcTemplate;

	public JdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}

	public void update(String sql) {
		jdbcTemplate.update(sql);

	}

	public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public boolean query(String sql) {

		jdbcTemplate.execute(sql);

		return false;
	}

	public long querlong(String sql) {

		return jdbcTemplate.queryForLong(sql);
	}

}
