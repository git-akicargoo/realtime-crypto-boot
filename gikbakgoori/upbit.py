import requests

def get_ticker_price(market):
  url = f"https://api.upbit.com/v1/ticker?markets={market}"
  headers = {"accept": "application/json"}
  response = requests.get(url, headers=headers)
  data = response.json()
  return data[0]['trade_price']
  
def get_tickers():
  url = "https://api.upbit.com/v1/market/all"
  headers = {"accept": "application/json"}
  response = requests.get(url, headers=headers)
  data = response.json()
  tickers = []
  for market in data:
    tickers.append(market['market'])
  return tickers
  
def get_market_categories(tickers):
    categories = set()
    for ticker in tickers:
        category = ticker.split('-')[0]  # KRW-BTC에서 KRW 부분 추출
        categories.add(category)
    return sorted(list(categories))

def filter_tickers_by_category(tickers, category):
    return [ticker for ticker in tickers if ticker.startswith(category + '-')]

def get_market_info(market):
    url = f"https://api.upbit.com/v1/ticker?markets={market}"
    headers = {"accept": "application/json"}
    response = requests.get(url, headers=headers)
    return response.json()[0]

def get_tickers_with_info(category=None):
    url = "https://api.upbit.com/v1/market/all"
    headers = {"accept": "application/json"}
    response = requests.get(url, headers=headers)
    markets = response.json()
    
    # 카테고리로 필터링
    if category:
        markets = [m for m in markets if m['market'].startswith(category + '-')]
    
    # 각 마켓의 상세 정보 가져오기
    market_infos = []
    total = len(markets)
    print(f"\n코인 정보를 가져오는 중... (총 {total}개)")
    
    for i, market in enumerate(markets, 1):
        ticker = market['market']
        print(f"\r진행중: {i}/{total} ({ticker})", end='')  # 진행상황 표시
        try:
            info = get_market_info(ticker)
            market_infos.append({
                'market': ticker,
                'trade_price': info['trade_price'],
                'signed_change_rate': info['signed_change_rate'] * 100,
                'acc_trade_price_24h': info['acc_trade_price_24h']
            })
        except Exception as e:
            print(f"\n{ticker} 정보 가져오기 실패: {str(e)}")
            continue
    
    print("\n완료!")
    return market_infos

def sort_markets(markets, sort_type):
    if sort_type == '1':  # 시가총액(거래대금) 순
        return sorted(markets, key=lambda x: x['acc_trade_price_24h'], reverse=True)
    elif sort_type == '2':  # 상승률 순
        return sorted(markets, key=lambda x: x['signed_change_rate'], reverse=True)
    elif sort_type == '3':  # 하락률 순
        return sorted(markets, key=lambda x: x['signed_change_rate'])
    return markets

def main():
    # 거래 화폐 종류 출력
    tickers = get_tickers()
    categories = get_market_categories(tickers)
    print("거래 화폐 종류:")
    for i, category in enumerate(categories, 1):
        print(f"{i}. {category}")
    
    # 거래 화폐 선택
    cat_selection = int(input("\n거래 화폐를 선택하세요 (번호 입력): ")) - 1
    selected_category = categories[cat_selection]
    
    # 정렬 방식 선택
    print("\n정렬 방식을 선택하세요:")
    print("1. 시가총액(거래대금) 순")
    print("2. 상승률 순")
    print("3. 하락률 순")
    sort_type = input("선택 (1-3): ")
    
    # 표시할 개수 선택
    print("\n표시할 코인 개수를 선택하세요:")
    print("1. 상위 10개")
    print("2. 상위 20개")
    print("3. 전체")
    display_option = input("선택 (1-3): ")
    
    # 코인 정보 가져오기 및 정렬
    markets = get_tickers_with_info(selected_category)
    sorted_markets = sort_markets(markets, sort_type)
    
    # 표시할 개수 설정
    if display_option == '1':
        sorted_markets = sorted_markets[:10]
    elif display_option == '2':
        sorted_markets = sorted_markets[:20]
    
    # 코인 목록 출력
    print(f"\n{selected_category} 코인 목록:")
    for i, market in enumerate(sorted_markets, 1):
        print(f"{i}. {market['market']} - 현재가: {market['trade_price']:,}원, "
              f"등락률: {market['signed_change_rate']:.2f}%, "
              f"거래대금: {market['acc_trade_price_24h']/1000000:.0f}백만원")
    
    # 코인 선택
    selection = int(input("\n가격을 확인하고 싶은 코인의 번호를 입력하세요: ")) - 1
    selected_market = sorted_markets[selection]
    print(f"\n{selected_market['market']}의 현재 가격: {selected_market['trade_price']:,}원")

if __name__ == "__main__":
  main()

  
# # API 참고
# https://docs.upbit.com/docs/create-authorization-request 개발자 문서
# https://api.upbit.com/v1/market/all 전체 코인 목록
# curl --request GET \
# --url https://api.upbit.com/v1/market/all
# curl --request GET \
# --url 'https://api.upbit.com/v1/ticker?markets=KRW-BTC'
# curl --request GET \
# --url 'https://api.upbit.com/v1/ticker?markets=KRW-DOGE'


# 구현 기획
# 레디스와 카프카는 자유롭게 .env 에 enable_redis=true 로 하면 활성화 되고 아니면 사용안하고 하게끔

# 레디스와 카프카를 사용할때랑 안할때

# 서버 성능이 어떻게 되는지 비교해보고 싶은데 

# 그래서 그렇게 코드를 만들고 진행해보고 싶은데

# 일단 설정 방향은 그렇게 잡고 웹소켓 API 만 사용해서 KRW 의 가격들 업비트의 BTC 가격만 실시간으로 받고 

# 추가로 다른 거래소의 BTC 가격들도 추가할수있게 해서

# 각 거래서의 가격차이를 테이블로 보고 쉽게 비교할수있게 해서 

# 가격차이가 어느정도 차이가 나면  상단의 테이블에 표시될수있게 구현하고 싶은데