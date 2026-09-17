package com.example

import com.example.data.usb.UsbChipType
import com.example.upload.Esp32Uploader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testUsbChipDetection() {
    assertEquals(UsbChipType.CP210X, UsbChipType.fromVidPid(0x10C4, 0xEA60))
    assertEquals(UsbChipType.CH340, UsbChipType.fromVidPid(0x1A86, 0x7523))
    assertEquals(UsbChipType.CH9102, UsbChipType.fromVidPid(0x1A86, 0x55D4))
    assertEquals(UsbChipType.FTDI, UsbChipType.fromVidPid(0x0403, 0x6001))
    assertEquals(UsbChipType.ESPRESSIF_CDC, UsbChipType.fromVidPid(0x303A, 0x1001))
    assertEquals(UsbChipType.ARDUINO_CDC, UsbChipType.fromVidPid(0x2341, 0x0043))
  }

  @Test
  fun testSlipFraming() {
    val raw = byteArrayOf(0xC0.toByte(), 0xDB.toByte(), 0x01, 0x02)
    val framed = Esp32Uploader.slipFrame(raw)
    assertEquals(0xC0.toByte(), framed.first())
    assertEquals(0xC0.toByte(), framed.last())
    val unframed = Esp32Uploader.slipUnframe(framed)
    assertArrayEquals(raw, unframed)
  }
}
