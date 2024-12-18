package com.android.car.docklib.view

import android.content.ComponentName
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.docklib.DockInterface
import com.android.car.docklib.data.DockAppItem
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class DockAdapterTest {

    private companion object {
        private const val PACKAGE = "package"
    }

    private val dockInterfaceMock = mock<DockInterface> {}
    private val dockItemViewHolderMock = mock<DockItemViewHolder> {}
    private val componentName = mock<ComponentName> {
        on { packageName } doReturn PACKAGE
    }
    private val runnableMock = mock<Runnable> {}
    private val contextMock = mock<Context> {}
    private val dockAdapter: DockAdapter = spy(DockAdapter(dockInterfaceMock, contextMock))
    private val dockItemList: MutableList<DockAppItem> = mutableListOf()

    @Before
    fun setup() {
        for (i in 0..5) {
            dockItemList.add(mock<DockAppItem> {})
        }
        dockAdapter.submitList(dockItemList)
        doReturn(componentName).whenever(dockItemList[1]).component
    }

    @After
    fun tearDown() {
        dockItemList.clear()
    }

    @Test
    fun onBindViewHolder_emptyPayload_onBindViewHolderWithoutPayloadCalled() {
        dockAdapter.onBindViewHolder(dockItemViewHolderMock, 1, MutableList(0) {})

        verify(dockAdapter).onBindViewHolder(eq(dockItemViewHolderMock), eq(1))
    }

    @Test
    fun onBindViewHolder_nullPayload_noop() {
        dockAdapter.onBindViewHolder(dockItemViewHolderMock, 1, MutableList(1) {})

        verifyNoMoreInteractions(dockItemViewHolderMock)
    }

    @Test
    fun onBindViewHolder_payloadOfIncorrectType_noop() {
        class DummyPayload

        dockAdapter.onBindViewHolder(dockItemViewHolderMock, 1, MutableList(1) { DummyPayload() })

        verifyNoMoreInteractions(dockItemViewHolderMock)
    }

    @Test
    fun onBindViewHolder_payloadChangeItemType_itemTypeChangedCalled() {
        dockAdapter.onBindViewHolder(
                dockItemViewHolderMock,
                1,
                MutableList(1) { DockAdapter.PayloadType.CHANGE_ITEM_TYPE }
        )

        verify(dockAdapter, never()).onBindViewHolder(eq(dockItemViewHolderMock), eq(1))
        verify(dockItemViewHolderMock).itemTypeChanged(eq(dockItemList[1]))
    }

    @Test
    fun onBindViewHolder_payloadMediaItemType_hasMediaSession_hasActiveMediaSessionCalled() {
        dockAdapter.onMediaSessionChange(listOf(PACKAGE))
        dockAdapter.onBindViewHolder(
            dockItemViewHolderMock,
            1,
            MutableList(1) { DockAdapter.PayloadType.CHANGE_ACTIVE_MEDIA_SESSION }
        )

        verify(dockAdapter, never()).onBindViewHolder(eq(dockItemViewHolderMock), eq(1))
        verify(dockItemViewHolderMock).setHasActiveMediaSession(true)
    }

    @Test
    fun onBindViewHolder_payloadMediaItemType_noMediaSession_hasActiveMediaSessionCalled() {
        dockAdapter.onBindViewHolder(
            dockItemViewHolderMock,
            1,
            MutableList(1) { DockAdapter.PayloadType.CHANGE_ACTIVE_MEDIA_SESSION }
        )

        verify(dockAdapter, never()).onBindViewHolder(eq(dockItemViewHolderMock), eq(1))
        verify(dockItemViewHolderMock).setHasActiveMediaSession(false)
    }

    @Test
    fun onBindViewHolder_multiplePayloadChangeItemType_itemTypeChangedCalledMultipleTimes() {
        dockAdapter.onBindViewHolder(
                dockItemViewHolderMock,
                1,
                MutableList(3) { DockAdapter.PayloadType.CHANGE_ITEM_TYPE }
        )

        verify(dockAdapter, never()).onBindViewHolder(eq(dockItemViewHolderMock), eq(1))
        verify(dockItemViewHolderMock, times(3)).itemTypeChanged(eq(dockItemList[1]))
    }

    @Test
    fun onBindViewHolder_setCallback_bindWithRunnable() {
        dockAdapter.setCallback(1, runnableMock)
        dockAdapter.onBindViewHolder(dockItemViewHolderMock, 1)

        verify(dockItemViewHolderMock).bind(
                eq(dockItemList[1]),
                eq(false),
                eq(runnableMock),
                any()
        )
    }
}
