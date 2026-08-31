package com.carelink.app.ui.elder;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import com.carelink.app.base.BaseFragment;
import com.carelink.app.databinding.FragmentFamilyContactBinding;

/** 老人端家属联系人页面 */
public class FamilyContactFragment extends BaseFragment<FragmentFamilyContactBinding> {

    @Override
    protected FragmentFamilyContactBinding inflateBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentFamilyContactBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        // TODO: 展示家属联系人列表，支持一键拨打
    }
}
