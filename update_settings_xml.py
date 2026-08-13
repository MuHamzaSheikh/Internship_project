content = '''<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@android:color/white">

    <ImageView
        android:id="@+id/settingsBack"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:layout_marginStart="16dp"
        android:layout_marginTop="16dp"
        android:src="@drawable/ic_back"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <TextView
        android:id="@+id/settingsTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Setting"
        android:textColor="@color/TextPrimary"
        android:textSize="20sp"
        android:textStyle="bold"
        app:layout_constraintTop_toTopOf="@id/settingsBack"
        app:layout_constraintBottom_toBottomOf="@id/settingsBack"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <ScrollView
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:fillViewport="true"
        android:layout_marginTop="16dp"
        app:layout_constraintTop_toBottomOf="@id/settingsBack"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingStart="24dp"
            android:paddingEnd="24dp"
            android:paddingTop="12dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="General"
                android:textColor="@color/TextPrimary"
                android:textSize="18sp"
                android:textStyle="bold" />

            <!-- Auto Resume -->
            <LinearLayout
                android:id="@+id/btnAutoResume"
                android:layout_width="match_parent"
                android:layout_height="64dp"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:background="?attr/selectableItemBackground">

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:src="@drawable/ic_auto_resume"
                    app:tint="@color/PrimaryBlue" />

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="16dp"
                    android:layout_weight="1"
                    android:text="Auto Resume"
                    android:textColor="@color/TextPrimary"
                    android:textSize="16sp" />

                <androidx.appcompat.widget.SwitchCompat
                    android:id="@+id/switchAutoResume"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:checked="true"
                    app:trackTint="@color/PrimaryBlue" />
            </LinearLayout>

            <!-- Languages -->
            <LinearLayout
                android:id="@+id/btnLanguages"
                android:layout_width="match_parent"
                android:layout_height="64dp"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:background="?attr/selectableItemBackground">

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:src="@drawable/ic_language"
                    app:tint="@color/PrimaryBlue" />

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="16dp"
                    android:layout_weight="1"
                    android:text="Languages"
                    android:textColor="@color/TextPrimary"
                    android:textSize="16sp" />

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:src="@drawable/ic_chevron_right"
                    app:tint="@color/TextPrimary" />
            </LinearLayout>

            <!-- Privacy Policy -->
            <LinearLayout
                android:id="@+id/btnPrivacyPolicy"
                android:layout_width="match_parent"
                android:layout_height="64dp"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:background="?attr/selectableItemBackground">

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:src="@drawable/ic_privacy"
                    app:tint="@color/PrimaryBlue" />

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="16dp"
                    android:layout_weight="1"
                    android:text="Privacy Policy"
                    android:textColor="@color/TextPrimary"
                    android:textSize="16sp" />
            </LinearLayout>

            <!-- About -->
            <LinearLayout
                android:id="@+id/btnAbout"
                android:layout_width="match_parent"
                android:layout_height="64dp"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:background="?attr/selectableItemBackground">

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:src="@drawable/ic_badge"
                    app:tint="@color/PrimaryBlue" />

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="16dp"
                    android:layout_weight="1"
                    android:text="About"
                    android:textColor="@color/TextPrimary"
                    android:textSize="16sp" />
            </LinearLayout>

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="24dp"
                android:text="Feedback"
                android:textColor="@color/TextPrimary"
                android:textSize="18sp"
                android:textStyle="bold" />

            <!-- Share with Friends -->
            <LinearLayout
                android:id="@+id/btnShare"
                android:layout_width="match_parent"
                android:layout_height="64dp"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:background="?attr/selectableItemBackground">

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:src="@drawable/ic_share_friends"
                    app:tint="@color/PrimaryBlue" />

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="16dp"
                    android:layout_weight="1"
                    android:text="Share with Friends"
                    android:textColor="@color/TextPrimary"
                    android:textSize="16sp" />
            </LinearLayout>

            <!-- Rate Us -->
            <LinearLayout
                android:id="@+id/btnRateUs"
                android:layout_width="match_parent"
                android:layout_height="64dp"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:background="?attr/selectableItemBackground">

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:src="@drawable/ic_rate_us"
                    app:tint="@color/PrimaryBlue" />

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="16dp"
                    android:layout_weight="1"
                    android:text="Rate Us"
                    android:textColor="@color/TextPrimary"
                    android:textSize="16sp" />
            </LinearLayout>

        </LinearLayout>
    </ScrollView>

</androidx.constraintlayout.widget.ConstraintLayout>
'''

with open('app/src/main/res/layout/activity_settings.xml', 'w') as f:
    f.write(content)
print("Updated activity_settings.xml")
