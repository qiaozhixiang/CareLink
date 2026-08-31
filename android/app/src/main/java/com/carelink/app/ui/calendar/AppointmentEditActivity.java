package com.carelink.app.ui.calendar;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.carelink.app.data.local.entity.AppointmentEntity;
import com.carelink.app.databinding.ActivityAppointmentEditBinding;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class AppointmentEditActivity extends AppCompatActivity {

    private ActivityAppointmentEditBinding binding;
    private AppointmentViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAppointmentEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(AppointmentViewModel.class);

        binding.btnSave.setOnClickListener(v -> {
            String title = binding.etTitle.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, "请填写标题", Toast.LENGTH_SHORT).show();
                return;
            }
            AppointmentEntity entity = new AppointmentEntity();
            entity.title = title;
            entity.category = "HOSPITAL";
            entity.startTime = System.currentTimeMillis();
            entity.endTime = System.currentTimeMillis() + 3600000;
            entity.status = 0;
            viewModel.save(entity);
            finish();
        });
    }
}
