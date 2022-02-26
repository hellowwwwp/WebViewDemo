package com.example.webviewdemo

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.webviewdemo.databinding.LayoutCommentItemBinding
import com.example.webviewdemo.ext.asAsyncDifferConfig
import com.example.webviewdemo.ext.toast


/**
 * @author: wangpan
 * @email: p.wang@aftership.com
 * @date: 2022/2/26
 */
class CommentAdapter : ListAdapter<CommentItem, CommentAdapter.CommentViewHolder>(
    CommentItem.comparator.asAsyncDifferConfig()
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = LayoutCommentItemBinding.inflate(inflater, parent, false)
        return CommentViewHolder(binding).also { holder ->
            holder.itemView.setOnClickListener {
                "click item: ${holder.layoutPosition}".toast()
            }
        }
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        Log.d("tag2", "CommentAdapter onBindViewHolder: $position")
        val item = getItem(position)
        holder.viewBinding.commentTv.text = item.content
    }

    override fun onViewRecycled(holder: CommentViewHolder) {
        super.onViewRecycled(holder)
        val position = holder.layoutPosition
        Log.d("tag2", "CommentAdapter onViewRecycled: $position")
    }

    class CommentViewHolder(
        val viewBinding: LayoutCommentItemBinding
    ) : RecyclerView.ViewHolder(viewBinding.root)
}