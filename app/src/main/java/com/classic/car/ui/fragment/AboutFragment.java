package com.classic.car.ui.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.classic.android.consts.MIME;
import com.classic.android.permissions.AfterPermissionGranted;
import com.classic.android.permissions.EasyPermissions;
import com.classic.android.utils.SDCardUtil;
import com.classic.car.R;
import com.classic.car.app.CarApplication;
import com.classic.car.consts.Consts;
import com.classic.car.db.BackupManager;
import com.classic.car.db.dao.ConsumerDao;
import com.classic.car.ui.activity.OpenSourceLicensesActivity;
import com.classic.car.ui.base.AppBaseFragment;
import com.classic.car.ui.widget.AuthorDialog;
import com.classic.car.utils.IntentUtil;
import com.classic.car.utils.PgyUtil;
import com.classic.car.utils.RxUtil;
import com.classic.car.utils.ToastUtil;
import com.classic.car.utils.UriUtil;
import com.jakewharton.rxbinding.view.RxView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.OnClick;
import rx.Observable;
import rx.functions.Action1;

/**
 * 应用名称: CarAssistant
 * 包 名 称: com.classic.car.ui.fragment
 *
 * 文件描述：关于页面
 * 创 建 人：续写经典
 * 创建时间：16/5/29 下午2:21
 */
public class AboutFragment extends AppBaseFragment {
    private static final int    REQUEST_CODE_FEEDBACK = 1001;
    private static final int    FILE_CHOOSER_CODE     = 1002;
    private static final String FEEDBACK_PERMISSION   = Manifest.permission.RECORD_AUDIO;

    @BindView(R.id.about_version) TextView    mVersion;
    @BindView(R.id.about_update)  TextView    mUpdate;
    @Inject                       ConsumerDao mConsumerDao;

    @SuppressWarnings("SpellCheckingInspection")
    private final SimpleDateFormat mDateFormat    = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA);
    private final BackupManager    mBackupManager = new BackupManager();

    private AuthorDialog mAuthorDialog;

    public static AboutFragment newInstance() {
        return new AboutFragment();
    }

    @Override public int getLayoutResId() {
        return R.layout.fragment_about;
    }

    @Override public void initView(View parentView, Bundle savedInstanceState) {
        super.initView(parentView, savedInstanceState);
        ((CarApplication) mActivity.getApplicationContext()).getAppComponent().inject(this);
        mVersion.setText(getString(R.string.about_version, getVersionName(mAppContext)));
        PgyUtil.setDialogStyle("#3F51B5", "#FFFFFF");
        addSubscription(RxView.clicks(mUpdate)
                              .throttleFirst(Consts.SHIELD_TIME, TimeUnit.SECONDS)
                              .subscribe(new Action1<Void>() {
                                  @Override public void call(Void aVoid) {
                                      PgyUtil.checkUpdate(mActivity, true);
                                  }
                              }));
    }

    @OnClick({R.id.about_feedback, R.id.about_author, R.id.about_thanks, R.id.about_share, R.id.about_backup,
            R.id.about_restore}) public void onClick(View view) {
        switch (view.getId()) {
            case R.id.about_feedback:
                checkRecordAudioPermissions();
                break;
            case R.id.about_author:
                if (null == mAuthorDialog) {
                    mAuthorDialog = new AuthorDialog(mActivity);
                }
                mAuthorDialog.show();
                break;
            case R.id.about_share:
                IntentUtil.shareText(mActivity, getString(R.string.share_title), getString(R.string.share_subject),
                                     getString(R.string.share_content));
                break;
            case R.id.about_thanks:
                OpenSourceLicensesActivity.start(mActivity);
                break;
            case R.id.about_backup:
                backup(createBackupFileName());
                break;
            case R.id.about_restore:
                IntentUtil.showFileChooser(this, MIME.FILE, R.string.select_backup_file_hint, FILE_CHOOSER_CODE,
                                           R.string.not_found_file_manager_hint);
                break;
        }
    }

    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode == Activity.RESULT_OK && requestCode == FILE_CHOOSER_CODE) {
            String path = UriUtil.toAbsolutePath(mAppContext, data.getData());
            if(!TextUtils.isEmpty(path) && path.endsWith(Consts.BACKUP_SUFFIX)) {
                //ToastUtil.showToast(mAppContext, "select file："+path);
                restore(path);
            } else {
                ToastUtil.showToast(mAppContext, R.string.invalid_backup_file);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void restore(@NonNull String path) {
        addSubscription(Observable.just(mBackupManager.restore(mConsumerDao, path))
                                  .compose(RxUtil.<Boolean>applySchedulers(RxUtil.IO_ON_UI_TRANSFORMER))
                                  .subscribe(new Action1<Boolean>() {
                                      @Override public void call(Boolean result) {
                                          ToastUtil.showToast(mAppContext, result ? R.string.restore_success :
                                                  R.string.restore_failure);
                                      }
                                  }, new Action1<Throwable>() {
                                      @Override public void call(Throwable throwable) {
                                          ToastUtil.showToast(mAppContext, R.string.restore_failure);
                                      }
                                  }));
    }

    private void backup(final String filePath) {
        addSubscription(Observable.just(mBackupManager.backup(mConsumerDao, filePath))
                                  .compose(RxUtil.<Integer>applySchedulers(RxUtil.IO_ON_UI_TRANSFORMER))
                                  .subscribe(new Action1<Integer>() {
                                      @Override public void call(Integer integer) {
                                          if (integer == 0) {
                                              ToastUtil.showToast(mAppContext, R.string.backup_empty);
                                          } else {
                                              ToastUtil.showToast(mAppContext, String.format(Locale.CHINA,
                                                                                             getString(R.string.backup_success), filePath));
                                          }
                                      }
                                  }, new Action1<Throwable>() {
                                      @Override public void call(Throwable throwable) {
                                          ToastUtil.showToast(mAppContext, R.string.backup_success);
                                      }
                                  }));

    }

    private String createBackupFileName() {
        //noinspection StringBufferReplaceableByString
        return new StringBuilder(SDCardUtil.getFileDirPath())
                .append(File.separator)
                .append(Consts.BACKUP_PREFIX)
                .append(mDateFormat.format(new Date(System.currentTimeMillis())))
                .append(Consts.BACKUP_SUFFIX)
                .toString();
    }

    @Override public void onPause() {
        super.onPause();
        if (null != mAuthorDialog && mAuthorDialog.isShowing()) {
            mAuthorDialog.dismiss();
        }
    }

    @AfterPermissionGranted(REQUEST_CODE_FEEDBACK) private void checkRecordAudioPermissions() {
        if (EasyPermissions.hasPermissions(mAppContext, FEEDBACK_PERMISSION)) {
            PgyUtil.feedback(mActivity);
        } else {
            EasyPermissions.requestPermissions(this, Consts.FEEDBACK_PERMISSIONS_DESCRIBE, REQUEST_CODE_FEEDBACK,
                                               FEEDBACK_PERMISSION);
        }
    }

    @Override public void onPermissionsGranted(int requestCode, List<String> perms) {
        super.onPermissionsGranted(requestCode, perms);
        if (requestCode == REQUEST_CODE_FEEDBACK) {
            PgyUtil.feedback(mActivity);
        }
    }

    @Override public void onPermissionsDenied(int requestCode, List<String> perms) {
        super.onPermissionsDenied(requestCode, perms);
        if (requestCode == REQUEST_CODE_FEEDBACK) {
            PgyUtil.feedback(mActivity);
        }
    }

    private String getVersionName(@NonNull Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo info = packageManager.getPackageInfo(context.getPackageName(), 0);
            if (null != info) {
                return info.versionName;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
