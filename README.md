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

### v1.12.0 linked study notes and excerpt basket

- 도구 → **듀얼 뷰 노트**: PDF와 독립 노트를 함께 표시합니다. 화면 폭 600dp 이상은 좌우, 작은 화면은 상하로 배치하고 폴드 펼침/접힘에 대응합니다.
- ＋로 현재 페이지에 연결된 노트를 추가하고, 본문 드래그 → **발췌**로 OCR 문장을 바구니에 저장합니다. 노트 본문을 눌러 원문과 설명을 수정하거나 삭제할 수 있습니다. Markdown 문법은 일반 텍스트로 작성·저장합니다.
- 노트의 **[p.N]** 링크는 PDF 페이지와 저장된 위치로 이동합니다. PDF 페이지를 이동하면 연결된 노트 카드가 강조됩니다.
- **노트·발췌 내보내기**: Markdown, UTF-8 CSV, XLSX, Anki 가져오기용 TSV(앞면=원문, 뒷면=설명·문서·페이지). 바구니 보기에서는 발췌만, 전체 노트 보기에서는 모두 내보냅니다.
- 주석·노트·발췌를 문서별 별도 JSON 사이드카 파일에 자동 저장합니다(앱 내부 저장소). 기존 주석은 저장 시 이전하며 원본 PDF를 수정하지 않습니다. 외부 파일로 보관하려면 **주석 백업**을 사용합니다.
- **주석 백업**에 노트·발췌도 포함합니다. **주석 백업 복원**에서 문서명을 확인하고 복원을 누르면 현재 문서의 주석·노트를 교체합니다. 페이지 범위나 파일 형식이 잘못되면 기존 자료를 유지합니다. 원본 PDF는 변경하지 않습니다.

다음 단계: 녹음과 필기 시점 연결, TTS 반복 구간, 선택영역 AI 요약·Q&A 포스트잇. 이 릴리스에는 포함하지 않습니다.

### v1.13.0 lasso capture

- **필기도구 → 올가미 · 영역 캡처** 또는 **도구 → 올가미 · 영역 캡처**를 선택하고 손가락/S펜으로 영역을 둘러 그립니다. 손을 떼면 경로가 자동으로 닫히고 미리보기가 나타납니다.
- **이미지 복사**, **PNG 저장**, **이미지 공유**, **글자 복사**, **다시 선택**, **선택 종료**를 제공합니다. 글자 복사는 이미 OCR로 인식된 단어 중 올가미 안에 중심이 있는 단어를 복사합니다.
- 원본 페이지 렌더링과 표시 중인 필기·하이라이트·포스트잇을 포함하고, 선택 표시·페이지 화살표·앱 메뉴는 제외합니다. 올가미 밖은 투명합니다. 캡처는 최대 800만 픽셀로 제한합니다.
- 올가미 모드에서도 두 손가락 확대를 지원하며, 확대 시작이나 페이지 이동 시 이전 선택을 취소합니다. 취소된 터치나 너무 작은 선택은 캡처하지 않습니다.
- 복사는 이미지 붙여넣기를 지원하는 대상 앱에서 사용할 수 있습니다. 지원하지 않는 앱에는 PNG 저장 또는 공유를 사용하세요. 공유용 임시 이미지는 앱 캐시에 저장하고 7일 지난 파일은 다음 캡처 시 정리합니다.

### v1.14.0 notebook workspace

- **문서·필기 검색**: 상단 돋보기 또는 도구에서 현재 PDF의 모든 페이지와 필기 영역을 OCR로 검색합니다. 메모·번역·듀얼뷰 노트·타이핑·URL은 저장된 글자도 검색합니다. 진행 중 중지할 수 있으며 결과를 누르면 해당 페이지·위치로 이동합니다. 손글씨는 OCR이 읽을 수 있는 글씨에 한해 검색됩니다.
- **가장자리 스와이프**: 기본으로 켜집니다. 좌우 가장자리 32dp에서 안쪽으로 한 손가락을 밀면 페이지가 바뀝니다. 본문에서 시작한 드래그는 글자 선택이나 확대 화면 이동으로 처리하며 페이지를 넘기지 않습니다. 도구에서 수직 가장자리 넘김이나 끄기를 선택할 수 있습니다. S펜·손가락 필기·올가미 영역 선택 중의 제스처는 필기를 우선합니다.
- **두 쪽 보기**: 도구에서 켜거나 끕니다. 1–2, 3–4 페이지를 함께 표시하고, 각 페이지에 독립적으로 선택·필기·올가미를 사용할 수 있습니다. 스와이프는 두 페이지씩 이동합니다. 화면을 펼치거나 가로로 놓으면 더 편하게 읽을 수 있습니다.
- **문서함**: 상단 폴더에서 앱 안의 폴더를 만들고 PDF를 열 수 있습니다. ‘문서함에 PDF 저장’은 현재 폴더에 원본 PDF와 앱의 주석을 함께 복사합니다. 이름이 같으면 다른 이름으로 저장합니다. 주석은 앱에서 별도 저장하며 다른 PDF 앱에 보이게 하려면 PDF 내보내기를 사용하세요.
- **새 노트**: 현재 문서함 폴더에 이름과 페이지 수(1~200)를 정해 A4 빈 노트를 만듭니다. 기존 필기도구로 쓰거나 도구의 **타이핑**을 선택해 넣을 위치를 터치합니다. 타이핑·이미지·링크는 선택·이동 모드에서 눌러 수정·위치·크기 조절·삭제할 수 있습니다.
- **직선**: 필기도구의 직선을 선택하고 시작점에서 끝점까지 드래그합니다. S펜 또는 손가락 필기 설정에 따르며 실행 취소·다시 실행·지우개를 사용할 수 있습니다.
- **이미지 붙여넣기**: 브라우저 등에서 복사한 클립보드 이미지 URI를 가져옵니다. URL이나 글자만 복사된 경우에는 저장된 이미지를 고르는 메뉴를 제공합니다. 가져온 이미지는 앱에 복사하며 본문을 터치해 배치합니다.
- **웹·유튜브 링크**: 넣을 위치를 터치한 뒤 http/https 주소를 입력합니다. 표시된 링크를 누르면 링크 열기·수정·삭제를 선택할 수 있습니다.
- **PDF 내보내기**: 원문, 손글씨, 타이핑, 이미지, 표시 중인 포스트잇을 하나의 PDF로 내보냅니다. 원문 페이지는 렌더링 이미지로 합쳐지므로 원래 벡터·선택 가능한 글자 구조는 유지하지 않습니다. 듀얼뷰 노트는 독립 노트이며 별도 Markdown/Excel 내보내기를 사용합니다. 링크 주소는 출력되며 클릭 가능한 PDF 링크 주석은 별도로 생성하지 않습니다.
- **XLS·XLSX 가져오기**: 문서 선택기에서 선택하면 기존 기기 내 LibreOffice 엔진으로 PDF 변환합니다. 변환용 복사본의 격자선과 셀 테두리를 제거하고 내용·수식을 보존합니다. 원본 엑셀은 수정하지 않습니다. 최초 변환 엔진 다운로드와 ARM 기기가 필요합니다.

## v1.15.0 — 문서함과 무제한 노트 페이지

- 문서함을 전체 화면의 표지 카드와 폴더 트리로 변경했습니다. `＋ 새로 만들기`에서 폴더·새 노트·파일 가져오기를 선택합니다. 좁은 화면에서는 `☰ 폴더`로 트리를 펼칩니다.
- 가져온 PDF는 선택한 문서함 폴더에 자동 복사됩니다. 원본 파일을 수정하지 않으며, 같은 원본 URI를 다시 열면 저장된 문서를 엽니다. 기존 외부 PDF 탭도 다시 열 때 문서함으로 복사하고 필기·메모를 함께 가져옵니다.
- 문서 카드의 `⋮`에서 이름 변경·복사·이동을 지원합니다. 열린 탭의 이름을 길게 눌러 이름을 변경할 수도 있습니다. 문서별 필기·타이핑·이미지·책갈피·연결 노트와 종이 설정을 함께 보존합니다. 같은 이름의 파일을 덮어쓰지 않습니다.
- 새 노트는 백지·줄노트·모눈종이 및 6가지 배경색을 선택하여 **1페이지**로 시작합니다. 페이지 수를 입력하지 않습니다. 마지막 장에서 다음으로 넘기면 같은 종이의 페이지가 하나씩 자동 추가됩니다. 두 쪽 보기에서도 지원합니다.
- 페이지 미리보기 맨 위의 `＋ 페이지`로 새 장을 추가할 수 있습니다. 기존에 가져온 PDF에도 원하는 종이·색상의 페이지를 덧붙일 수 있습니다. 기존 PDF의 텍스트와 페이지를 이미지로 바꾸지 않고 보존합니다.
- 새 노트의 종이·색상은 실제 PDF 페이지 내용입니다. 앱 재실행, 복사·이동, PDF 내보내기 후에도 유지됩니다. v1.14.0에서 만든 노트는 자동 추가 표시가 없으므로 `＋ 페이지`를 이용할 수 있습니다.
- PDFBox Android 2.0.27.0(Apache-2.0)을 페이지 생성·추가에 사용합니다. 수정 권한이 없는 PDF나 읽을 수 없는 파일은 추가를 중단하며, 원본 저장 파일은 교체 전에 보존합니다. 모든 문서 관리와 PDF 편집은 기기에서 처리됩니다.
