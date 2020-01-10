/*
 * Copyright Chris2018998
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.beecp.pool;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static cn.beecp.util.BeecpUtil.oclose;

/**
 * Statement cache
 *
 * @author Chris.liao
 * @version 1.0
 */
final class StatementCache {
	private int capacity;
	private CacheNode head=null;//old
	private CacheNode tail=null;//new
	private HashMap<Object,CacheNode>nodeMap;
	public StatementCache(int capacity) {
		this.capacity=capacity;
		this.nodeMap = new HashMap<Object,CacheNode>((int)Math.ceil(capacity/0.75f),0.75f);
	}
	public PreparedStatement get(Object k) {
		CacheNode n = nodeMap.get(k);
		if (n != null) {
			moveToTail(n);
			return n.v;
		}
		return null;
	}
	public void put(Object k,PreparedStatement v) {
		CacheNode n = nodeMap.get(k);
		if (n==null) {
			n = new CacheNode(k,v);
			nodeMap.put(k,n);
			addNewNode(n);
			
			if(nodeMap.size()>capacity) {
			  CacheNode oldHead=removeHead();
			  nodeMap.remove(oldHead.k);
			  onRemove(oldHead.v);
			}
		} else {
			n.v = v;
			moveToTail(n);
		}
	}

	void clear() {
		Iterator<Map.Entry<Object, CacheNode>> itor=nodeMap.entrySet().iterator();
		while (itor.hasNext()) {
			Map.Entry<Object,CacheNode> entry=itor.next();
			itor.remove();
			 CacheNode node= entry.getValue();
			 onRemove(node.v);
		}
		
		head=null;
		tail=null;
	}
	private void onRemove(PreparedStatement obj) {
		oclose(obj);
	}
	//add new node
	private void addNewNode(CacheNode n) {
		if (head == null) {
			head = n;
			tail = n;
		} else {
			tail.next = n;
			n.pre = tail;
			tail = n;
		}
	}
	//below are node chain operation method
	private void moveToTail(CacheNode n) {
		if(n==tail)return;
		//remove from chain
		if (head == n) {//at head
			head = n.next;
			head.pre = null;
		} else {//at middle
			n.pre.next = n.next;
			n.next.pre = n.pre;
		}

		//append to tail
		tail.next = n;
		n.pre = tail;
		n.next = null;
		tail = tail.next;//new tail
	}
	//remove head when size more than capacity
	private CacheNode removeHead() {
		CacheNode n = head;
		if (head == tail) {
			head = null;
			tail = null;
		} else {
			head = head.next;
			head.pre = null;
		}
		return n;
	}
	final static class CacheNode {// double linked chain node
		private Object k;
		private PreparedStatement v;
		private CacheNode pre = null;
		private CacheNode next = null;
		private CacheNode(Object k, PreparedStatement v) {
			this.k = k;
			this.v = v;
		}
	}
}

final class PsCacheKey{
	private String sql;
	private int autoGeneratedKeys;
	private int[] columnIndexes=null;
	private String[] columnNames=null;
	private int resultSetType=0; 
	private int resultSetConcurrency=0;
	private int resultSetHoldability=0;

	private final static int prime=31;
	private final static int TYPE1=11*prime;
	private final static int TYPE2=22*prime;
	private final static int TYPE3=33*prime;
	private final static int TYPE4=44*prime;
	private final static int TYPE5=55*prime;
	private final static int TYPE6=66*prime;
	private int type=TYPE1;
	private int hashCode=TYPE1;
	
	public PsCacheKey(String sql) {
		this.sql=sql;
		
		hashCode =+sql.hashCode();	
	}
	public PsCacheKey(String sql, int autoGeneratedKeys) {
		this.type=TYPE2;
		this.sql=sql;
		this.autoGeneratedKeys=autoGeneratedKeys;
		
		hashCode = TYPE2 + autoGeneratedKeys;
		hashCode = prime * hashCode + sql.hashCode();
	}
	public PsCacheKey(String sql, int[] columnIndexes) {
		this.type=TYPE3;
		this.sql=sql;
		this.columnIndexes=columnIndexes;

		hashCode = TYPE3 + Arrays.hashCode(columnIndexes);
		hashCode = prime * hashCode + sql.hashCode();
	}
	public PsCacheKey(String sql, String[] columnNames) {
		this.type=TYPE4;
		this.sql=sql;
		this.columnNames=columnNames;
	
		hashCode = TYPE4 + Arrays.hashCode(columnNames);
		hashCode = prime * hashCode + sql.hashCode();
	}
	public PsCacheKey(String sql, int resultSetType, int resultSetConcurrency) {
		this.type=TYPE5;
		this.sql=sql;
		this.resultSetType=resultSetType;
		this.resultSetConcurrency=resultSetConcurrency;
		
		hashCode = TYPE5 + resultSetType;
		hashCode = prime * hashCode + resultSetConcurrency;
		hashCode = prime * hashCode + sql.hashCode();
	}
	public PsCacheKey(String sql, int resultSetType, int resultSetConcurrency,int resultSetHoldability) {
		this.type=TYPE6;
		this.sql=sql;
		this.resultSetType=resultSetType;
		this.resultSetConcurrency=resultSetConcurrency;
		this.resultSetHoldability=resultSetHoldability;
	
		hashCode = TYPE6 + resultSetType;
		hashCode = prime * hashCode + resultSetConcurrency;
		hashCode = prime * hashCode + resultSetHoldability;
		hashCode = prime * hashCode + sql.hashCode();
	}
	
	public int hashCode(){
		return hashCode;
	}
	public boolean equals(Object obj) {
		if(!(obj instanceof PsCacheKey))return false;
		PsCacheKey other=(PsCacheKey)obj;
		if(this.type==other.type) {
			switch(this.type){
			   case TYPE1:return this.sql.equals(other.sql);
			   case TYPE2:return autoGeneratedKeys==other.autoGeneratedKeys && this.sql.equals(other.sql);
			   case TYPE3:return Arrays.equals(columnIndexes,other.columnIndexes)&& this.sql.equals(other.sql);
			   case TYPE4:return Arrays.equals(columnNames,other.columnNames)&& this.sql.equals(other.sql);
			   case TYPE5:return resultSetType==other.resultSetType && resultSetConcurrency==other.resultSetConcurrency && this.sql.equals(other.sql);
			   case TYPE6:return resultSetType==other.resultSetType && resultSetConcurrency==other.resultSetConcurrency && resultSetHoldability==other.resultSetHoldability && this.sql.equals(other.sql);
			   default:return false;
			}
		}else{
			return false;
		}
	}
}

final class CsCacheKey{
	private String sql;
	private int resultSetType=0; 
	private int resultSetConcurrency=0;
	private int resultSetHoldability=0;
	
	private final static int prime=31;
	private final static int TYPE1=77*prime;
	private final static int TYPE2=88*prime;
	private final static int TYPE3=99*prime;
	
	private int type=TYPE1;
	private int hashCode;
	public CsCacheKey(String sql) {
		this.sql=sql;
		hashCode=TYPE1;
		hashCode =+sql.hashCode();	
	}
	
	public CsCacheKey(String sql, int resultSetType, int resultSetConcurrency) {
		this.type=TYPE2;
		this.sql=sql;
		this.resultSetType=resultSetType;
		this.resultSetConcurrency=resultSetConcurrency;
		
		hashCode = TYPE2 + resultSetType;
		hashCode = prime * hashCode + resultSetConcurrency;
		hashCode = prime * hashCode + sql.hashCode();
	}
	
	public CsCacheKey(String sql, int resultSetType, int resultSetConcurrency,int resultSetHoldability) {
		this.type=TYPE3;
		this.sql=sql;
		this.resultSetType=resultSetType;
		this.resultSetConcurrency=resultSetConcurrency;
		this.resultSetHoldability=resultSetHoldability;
		
		hashCode = TYPE3 + resultSetType;
		hashCode = prime * hashCode + resultSetConcurrency;
		hashCode = prime * hashCode + resultSetHoldability;
		hashCode = prime * hashCode + sql.hashCode();
	}
	public int hashCode(){
		return hashCode;
	}
	public boolean equals(Object obj) {
		if(!(obj instanceof CsCacheKey))return false;
		CsCacheKey other = (CsCacheKey)obj;
		if(this.type==other.type) {
			switch(this.type){
			  case TYPE1:return this.sql.equals(other.sql);
			  case TYPE2:return resultSetType==other.resultSetType && resultSetConcurrency==other.resultSetConcurrency && this.sql.equals(other.sql);
			  case TYPE3:return resultSetType==other.resultSetType && resultSetConcurrency==other.resultSetConcurrency && resultSetHoldability==other.resultSetHoldability && this.sql.equals(other.sql);
			  default:return false;
			}
		}else{
			return false;
		}
	}
}