package com.example.repository

import android.content.Context
import com.example.model.PermissionItem
import kotlinx.coroutines.flow.Flow

interface PermissionRepository {
    fun getPermissionsFlow(context: Context): Flow<List<PermissionItem>>
    fun checkPermissions(context: Context): List<PermissionItem>
    fun isAutostartConfirmed(): Boolean
    fun setAutostartConfirmed(confirmed: Boolean)
}
