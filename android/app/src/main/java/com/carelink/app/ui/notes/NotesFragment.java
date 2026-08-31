package com.carelink.app.ui.notes;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.carelink.app.data.local.entity.CareNoteEntity;
import com.carelink.app.databinding.FragmentNotesBinding;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class NotesFragment extends Fragment implements AddNoteDialogFragment.NoteSavedListener {
    private FragmentNotesBinding binding;
    private NoteViewModel viewModel;
    private NoteAdapter adapter;

    @Nullable
    @Override
    public android.view.View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable android.view.ViewGroup container,
                                          @Nullable Bundle savedInstanceState) {
        binding = FragmentNotesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(NoteViewModel.class);
        adapter = new NoteAdapter();
        binding.rvNotes.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvNotes.setAdapter(adapter);
        observeNotes();
        binding.fabAddNote.setOnClickListener(v -> new AddNoteDialogFragment().show(getChildFragmentManager(), "add_note"));
    }

    private void observeNotes() {
        viewModel.getNotes().observe(getViewLifecycleOwner(), notes -> adapter.submitList(notes));
    }

    @Override
    public void onNoteSaved(String noteContent, boolean important) {
        CareNoteEntity entity = new CareNoteEntity();
        entity.content = noteContent;
        entity.isImportant = important ? 1 : 0;
        entity.tags = important ? "重要" : "日常";
        entity.createdAt = System.currentTimeMillis();
        viewModel.save(entity);
        Toast.makeText(requireContext(), "备注已保存", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}



