package com.carelink.app.ui.notes;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.carelink.app.databinding.DialogAddNoteBinding;

/** 新增备注对话框 */
public class AddNoteDialogFragment extends DialogFragment {

    public interface NoteSavedListener {
        void onNoteSaved(String noteContent, boolean important);
    }

    private DialogAddNoteBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogAddNoteBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnSave.setOnClickListener(v -> {
            String content = binding.etContent.getText().toString().trim();
            if (content.isEmpty()) {
                Toast.makeText(requireContext(), "备注内容不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            if (getParentFragment() instanceof NoteSavedListener) {
                ((NoteSavedListener) getParentFragment()).onNoteSaved(content, binding.cbImportant.isChecked());
            }
            dismiss();
        });

        binding.btnCancel.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
