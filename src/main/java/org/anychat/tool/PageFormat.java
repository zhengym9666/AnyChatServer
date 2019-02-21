package org.anychat.tool;

public class PageFormat {
	/**
	 * 分页工具
	 * 
	 * @param currentPage
	 *            当前页
	 * @param pageSize
	 *            每页条数
	 * @param allNum
	 *            总数量
	 * @return
	 */
	public static PageObj getStartAndEnd(int currentPage, int pageSize, int allNum) {
		if (pageSize < 1) {
			pageSize = allNum;
		}
		int totalPage = (int) Math.ceil(allNum / (pageSize + 0.0));
		if (currentPage > totalPage) {
			currentPage = totalPage;
		}
		if (currentPage < 1) {
			currentPage = 1;
		}
		int start = (currentPage - 1) * pageSize;
		int end = (currentPage) * pageSize;
		if (end > allNum) {
			end = allNum;
		}
		return new PageObj(start, end, allNum, totalPage, currentPage, pageSize);
	}
}
