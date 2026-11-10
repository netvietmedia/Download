package com.ushareit.ui.intro

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nextai.sharenow.R
import com.nextai.sharenow.databinding.ItemIntroLanguageBinding
import com.ushareit.core.locale.AppLanguage
import com.ushareit.core.locale.LocaleManager

class IntroLanguageAdapter(
    private val languages: List<AppLanguage> = LocaleManager.SUPPORTED_LANGUAGES,
    private var selectedCode: String = LocaleManager.getSavedLanguage(),
    private val onLanguageSelected: (AppLanguage) -> Unit
) : RecyclerView.Adapter<IntroLanguageAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemIntroLanguageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIntroLanguageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = languages[position]
        val isSelected = item.code.equals(selectedCode, ignoreCase = true)

        holder.binding.apply {
            tvLangFlag.text = item.flag
            tvLangNativeName.text = item.nativeName
            tvLangEnglishName.text = item.displayName
            rbLangSelected.isChecked = isSelected

            cardLanguage.setBackgroundResource(
                if (isSelected) R.drawable.bg_language_item_selected
                else R.drawable.bg_language_item_normal
            )

            cardLanguage.setOnClickListener {
                if (selectedCode != item.code) {
                    val prevIndex = languages.indexOfFirst { it.code == selectedCode }
                    selectedCode = item.code
                    notifyItemChanged(prevIndex)
                    notifyItemChanged(position)
                    onLanguageSelected(item)
                }
            }
        }
    }

    override fun getItemCount(): Int = languages.size

    fun getSelectedLanguageCode(): String = selectedCode

    fun updateSelectedCode(code: String) {
        selectedCode = code
        notifyDataSetChanged()
    }
}
