package com.timemachine.keyboard.privacy

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.timemachine.keyboard.history.BlockReason

/**
 * Decide se um campo pode ser gravado. Recebe só inteiros e strings para ser testável na JVM.
 *
 * A classificação depende do que o app declara no EditorInfo. Um app que marca um campo de
 * senha como texto comum não é detectado: por isso existem o modo privado e a lista de apps
 * excluídos, e a interface nunca promete proteção absoluta.
 */
object PrivacyPolicy {

    fun evaluate(
        inputType: Int,
        imeOptions: Int,
        packageName: String?,
        privateMode: Boolean,
        excludedPackages: Set<String>,
    ): BlockReason? {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when {
            privateMode -> BlockReason.PRIVATE_MODE
            packageName != null && packageName in excludedPackages -> BlockReason.EXCLUDED_APP
            isPassword(inputClass, variation) -> BlockReason.PASSWORD
            imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0 ->
                BlockReason.NO_PERSONALIZED_LEARNING
            inputClass == InputType.TYPE_NULL -> BlockReason.UNSUPPORTED_FIELD
            inputClass != InputType.TYPE_CLASS_TEXT -> BlockReason.NUMERIC_FIELD
            else -> null
        }
    }

    fun isPassword(inputClass: Int, variation: Int): Boolean = when (inputClass) {
        InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}
