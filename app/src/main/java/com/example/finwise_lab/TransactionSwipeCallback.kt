package com.example.finwise_lab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import java.lang.reflect.Method

class TransactionSwipeCallback(
    private val context: Context,
    private val onDelete: (Int) -> Unit,
    private val onUpdate: (Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_delete)
    private val updateIcon = ContextCompat.getDrawable(context, R.drawable.ic_edit)
    private val deleteBackground = ColorDrawable(ContextCompat.getColor(context, R.color.red))
    private val updateBackground = ColorDrawable(ContextCompat.getColor(context, R.color.blue))
    private val clearPaint = Paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        when (direction) {
            ItemTouchHelper.LEFT -> onDelete(position)
            ItemTouchHelper.RIGHT -> onUpdate(position)
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        val itemHeight = itemView.bottom - itemView.top
        val isCanceled = dX == 0f && !isCurrentlyActive

        if (isCanceled) {
            clearCanvas(c, itemView.left.toFloat(), itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }

        // Draw background and icon based on swipe direction
        if (dX > 0) { // Swiping right (Update)
            // Draw update background
            updateBackground.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
            updateBackground.draw(c)

            // Draw update icon
            val updateIconTop = itemView.top + (itemHeight - updateIcon!!.intrinsicHeight) / 2
            val updateIconMargin = (itemHeight - updateIcon.intrinsicHeight) / 2
            val updateIconLeft = itemView.left + updateIconMargin
            val updateIconRight = itemView.left + updateIconMargin + updateIcon.intrinsicWidth
            val updateIconBottom = updateIconTop + updateIcon.intrinsicHeight
            updateIcon.setBounds(updateIconLeft, updateIconTop, updateIconRight, updateIconBottom)
            updateIcon.draw(c)
        } else if (dX < 0) { // Swiping left (Delete)
            // Draw delete background
            deleteBackground.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
            deleteBackground.draw(c)

            // Draw delete icon
            val deleteIconTop = itemView.top + (itemHeight - deleteIcon!!.intrinsicHeight) / 2
            val deleteIconMargin = (itemHeight - deleteIcon.intrinsicHeight) / 2
            val deleteIconLeft = itemView.right - deleteIconMargin - deleteIcon.intrinsicWidth
            val deleteIconRight = itemView.right - deleteIconMargin
            val deleteIconBottom = deleteIconTop + deleteIcon.intrinsicHeight
            deleteIcon.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom)
            deleteIcon.draw(c)
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun clearCanvas(c: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        c.drawRect(left, top, right, bottom, clearPaint)
    }
    
    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        // Check if the item is a month header
        val position = viewHolder.adapterPosition
        if (position != RecyclerView.NO_POSITION) {
            val adapter = recyclerView.adapter
            if (adapter is TransactionAdapter) {
                val item = adapter.currentList.getOrNull(position)
                if (item is TransactionItem.MonthHeader) {
                    // Return 0 to disable swiping for month headers
                    return 0
                }
            }
        }
        
        // Allow swiping for transaction items
        return super.getSwipeDirs(recyclerView, viewHolder)
    }
} 