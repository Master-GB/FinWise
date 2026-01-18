package com.example.finwise_lab

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.viewbinding.ViewBinding

class ActivityMyAccountBinding private constructor(
    private val rootView: View,
    val toolbar: Toolbar,
    val btnBack: ImageButton,
    val tvTitle: TextView,
    val ivProfilePicture: ImageView,
    val tvChangePhoto: TextView,
    val tvName: TextView,
    val tvEmail: TextView,
    val tvPhone: TextView,
    val layoutName: View,
    val layoutEmail: View,
    val layoutPhone: View
) : ViewBinding {

    override fun getRoot(): View = rootView

    companion object {
        fun inflate(inflater: LayoutInflater): ActivityMyAccountBinding {
            val root = inflater.inflate(R.layout.activity_my_account, null, false)

            return ActivityMyAccountBinding(
                rootView = root,
                toolbar = root.findViewById(R.id.toolbar),
                btnBack = root.findViewById(R.id.btnBack),
                tvTitle = root.findViewById(R.id.tvTitle),
                ivProfilePicture = root.findViewById(R.id.ivProfilePicture),
                tvChangePhoto = root.findViewById(R.id.tvChangePhoto),
                tvName = root.findViewById(R.id.tvName),
                tvEmail = root.findViewById(R.id.tvEmail),
                tvPhone = root.findViewById(R.id.tvPhone),
                layoutName = root.findViewById(R.id.layoutName),
                layoutEmail = root.findViewById(R.id.layoutEmail),
                layoutPhone = root.findViewById(R.id.layoutPhone)
            )
        }
    }
}