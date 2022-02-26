package com.example.webviewdemo

import androidx.recyclerview.widget.DiffUtil

/**
 * @author: wangpan
 * @email: p.wang@aftership.com
 * @date: 2022/2/26
 */
data class CommentItem(
    val id: Int,
    val content: String,
) {

    companion object {
        val comparator = object : DiffUtil.ItemCallback<CommentItem>() {
            override fun areItemsTheSame(oldItem: CommentItem, newItem: CommentItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: CommentItem, newItem: CommentItem): Boolean {
                return oldItem == newItem
            }
        }
    }

}