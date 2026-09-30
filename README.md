# PDF Note

Android 문서 독서·주석 앱입니다. 문서와 주석은 앱 내부에서 처리하고 문서별로 저장합니다.

## 주요 기능

- Android 파일 선택기로 PDF·HWP·HWPX·DOC·DOCX·PPT·PPTX 선택
- DOC·DOCX·PPT·PPTX 선택 시 기기 내 LibreOffice 엔진으로 PDF 자동 변환 후 다운로드/PDF Note 폴더에 저장하고 열기
- HWP·HWPX는 앱에 포함된 rhwptopdf 엔진으로 오프라인 PDF 변환·저장·열기(본문·표·그림, 지원 범위는 문서에 따라 다름)
- 실제 색상을 보면서 고르는 5색 하이라이트(영역 드래그)
- 하이라이트별 메모와 독립 메모를 포스트잇으로 추가·수정·삭제
- 메모·번역 포스트잇 펼치기, 최소화 및 숨기기
- OCR 단어에서 문장 끝까지 끊김 없이 드래그 선택
- 선택 문장 하이라이트·복사·번역·읽어주기·단어장 검색·개요·메모
- Translate 앱 직접 연결과 기기 TTS 미국·영국·호주 발음 선택
- 페이지 즐겨찾기 및 목록 이동
- 전체 페이지 썸네일 사이드바와 빠른 페이지 이동
- 여러 PDF를 동시에 열어 전환·닫는 문서 탭
- 페이지의 정확한 지점에 제목을 붙이는 문서별 개요
- S펜 필압 필기, 획 지우개, 실행 취소·다시 실행
- 필기도구 메뉴에서 펜·하이라이트·지우개·색상 선택 통합, 손가락 필기 켜기/끄기
- 메모·하이라이트 전체 목록과 페이지 바로가기
- 상태·내비게이션 바와 겹치지 않는 반응형 UI
- PDF에 집중할 수 있는 몰입형 전체 화면
- 페이지 번호로 이동
- 최근 문서 다시 열기
- 핀치 확대(1×~4×) 및 확대 화면 한 손가락 상하좌우 이동
- 본문 양옆 반투명 페이지 화살표와 전환 애니메이션; 스와이프는 기본 꺼짐, 설정에서 수평·수직 선택
- 문서별 주석 자동 저장
- 주석과 즐겨찾기 JSON 백업
- 저장소 전체 접근 권한 불필요

> DOC·DOCX·PPT·PPTX의 첫 자동 변환에는 약 48MB의 무료 LibreOffice 엔진을 한 번 다운로드합니다. 이후 문서는 기기에서 변환되며, PDF는 Android 10 이상에서 다운로드/PDF Note 폴더에 저장됩니다. 원본과 PDF의 배치는 글꼴·오피스 기능에 따라 달라질 수 있습니다. 변환 실패 시 설치된 문서 앱에서 원본을 열거나 PDF를 수동으로 가져올 수 있습니다. HWP·HWPX는 번들 WASM 엔진과 기기 글꼴을 사용해 변환합니다(입력 48MB 이하, 출력 128MB 이하, 3분 제한). 복잡한 문서·일부 개체·글꼴은 원본과 다를 수 있으며 실패 시 외부 앱의 PDF 내보내기를 이용할 수 있습니다.  HWP·DOC 본문 글자 미리보기에서는 표·그림·쪽 배치가 재현되지 않습니다. 주석은 원본 파일 자체에 삽입되지 않습니다.

## 요구 사항

- Android 8.0 (API 26) 이상
- 암호화되지 않은 PDF; 원본 HWP·DOC·PPT 등을 보려면 해당 형식을 여는 앱이 설치되어 있어야 합니다.
- 자동 변환은 ARM 64비트 또는 32비트 기기에서 지원합니다. 엔진 설치 시 네트워크와 충분한 저장 공간이 필요합니다.

## 빌드

Android Studio에서 프로젝트를 열거나 다음 명령을 실행합니다.

```bash
python3 tools/prepare_hwp_engine.py
gradle assembleDebug
```

GitHub Actions의 `Android CI` 워크플로는 push마다 디버그 APK를 빌드합니다. `v`로 시작하는 태그(예: `v1.0.0`)를 push하면 바로 설치 가능한 APK와 GitHub Release를 생성합니다. 현재 공개 릴리스 APK는 편의상 Android 디버그 키로 서명되므로 Play Store 배포 전에는 별도 비공개 키를 설정해야 합니다.

## 개인정보

OCR, 주석, 오피스 변환과 본문 미리보기는 기기에서 처리합니다. 자동 변환 엔진과 오프라인 번역 모델을 처음 받을 때 인터넷을 사용하며, 외부 Translate 앱을 선택하면 선택 문장이 해당 앱으로 전달됩니다. 메모와 즐겨찾기는 기기에 저장됩니다.

## 변환 엔진 저작권

LibreOfficeKit 런타임은 LibreOffice-Lite v2.0의 검증된 ARM 압축 파일을 최초 사용 시 내려받습니다. LibreOffice는 MPL-2.0이며 엔진 출처는 https://github.com/vasuki-re/LibreOffice-Lite 입니다. 런타임 설치·JNI 연동 방식은 MIT 라이선스의 ClearPDF 프로젝트(https://github.com/Chethan616/ClearPDF)를 참고했습니다. 앱의 엔진 설치 코드는 SHA-256과 파일 크기를 확인하고 원본 문서를 변환 서버에 전송하지 않습니다. LibreOfficeKit 런타임은 앱 소스 저장소에 포함하지 않습니다.

HWP/HWPX 변환은 rhwptopdf v0.2.2(MIT, rhwp 유래 모듈 Apache-2.0)를 사용합니다. 빌드 전에 고정된 SHA-256으로 검증한 JavaScript/WASM 파일을 다운로드해 APK에 포함합니다. 해당 라이선스와 NOTICE는 앱 assets/hwp에 포함됩니다. 기기에서 변환할 때 네트워크 접근은 차단됩니다.

## 라이선스

MIT


### v1.11.1 HWP font compatibility

HWP/HWPX conversion retries unsupported PDF font embedding with vector text outlines. This handles Android system-font subsetting failures while retaining layout and graphics. Text can remain selectable on pages whose fonts embed successfully; fallback pages use outlines and may require OCR for text selection. Conversion failures now show the complete error in a dialog.

The offline engine is built from pinned rhwptopdf source (`adbc4bf0f5c6e041ed65b0dbc8aa6b810990a48b`) with the reviewed patch in `tools/prepare_hwp_engine.py`. CI runs a Korean TTC font regression before building the WASM and APK. Local builds require the pinned source checkout at `.hwp-engine-source`, Rust, wasm-pack 0.13.1, Node.js, and `PDFNOTE_TEST_FONT` pointing to a Korean TTC font.

### v1.11.2 repeated text selection

Touching a word starts a fresh selection even near the previous selection handles. Touching a handle still adjusts the selected range. Selection menus allow outside touches, and cancellation clears pending long presses. OCR regions are copied into the page view so changing pages or documents cannot erase the session cache. CI runs regression tests for consecutive drags, handle adjustment, cached-page return, selection actions, and canceled gestures before publishing the APK.
