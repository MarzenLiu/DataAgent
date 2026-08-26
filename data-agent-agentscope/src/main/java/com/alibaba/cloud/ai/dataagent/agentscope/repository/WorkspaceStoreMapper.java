/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.alibaba.cloud.ai.dataagent.agentscope.repository;

import com.alibaba.cloud.ai.dataagent.agentscope.entity.WorkspaceStoreRow;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WorkspaceStoreMapper {

	@Select("""
			SELECT store_key, namespace_key, item_key, value_json, version
			FROM agent_workspace_store
			WHERE store_key = #{storeKey}
			""")
	WorkspaceStoreRow findByStoreKey(@Param("storeKey") String storeKey);

	@Select("""
			SELECT store_key, namespace_key, item_key, value_json, version
			FROM agent_workspace_store
			WHERE namespace_hash = #{namespaceHash} AND namespace_key = #{namespaceKey}
			ORDER BY item_key
			LIMIT #{limit} OFFSET #{offset}
			""")
	List<WorkspaceStoreRow> findByNamespace(@Param("namespaceHash") String namespaceHash,
			@Param("namespaceKey") String namespaceKey, @Param("limit") int limit, @Param("offset") int offset);

	@Insert("""
			INSERT INTO agent_workspace_store
				(store_key, namespace_hash, namespace_key, item_key, value_json, version, create_time, update_time)
			VALUES
				(#{storeKey}, #{namespaceHash}, #{namespaceKey}, #{itemKey}, #{valueJson}, 1,
				 CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
			""")
	int insert(@Param("storeKey") String storeKey, @Param("namespaceHash") String namespaceHash,
			@Param("namespaceKey") String namespaceKey, @Param("itemKey") String itemKey,
			@Param("valueJson") String valueJson);

	@Update("""
			UPDATE agent_workspace_store
			SET value_json = #{valueJson}, version = version + 1, update_time = CURRENT_TIMESTAMP
			WHERE store_key = #{storeKey}
			""")
	int update(@Param("storeKey") String storeKey, @Param("valueJson") String valueJson);

	@Update("""
			UPDATE agent_workspace_store
			SET value_json = #{valueJson}, version = version + 1, update_time = CURRENT_TIMESTAMP
			WHERE store_key = #{storeKey} AND version = #{expectedVersion}
			""")
	int updateIfVersion(@Param("storeKey") String storeKey, @Param("valueJson") String valueJson,
			@Param("expectedVersion") long expectedVersion);

	@Delete("DELETE FROM agent_workspace_store WHERE store_key = #{storeKey}")
	int delete(@Param("storeKey") String storeKey);

}
